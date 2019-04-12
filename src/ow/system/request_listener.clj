(ns ow.system.request-listener
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(def ^:private ^:dynamic *request-map* {})

(defn- trace-info [& [this]]
  (-> (select-keys *request-map* #{:id :flowid})
      (assoc :name (or (and this (:name this))
                       "(n/a)"))))

(defn- trace-request [msg & [this]]
  (log/trace (trace-info this) msg))

(defn init-request-response-channels-xf [rf]
  (let [out-ch  (a/chan)
        in-mult (a/mult out-ch)]
    (fn
      ([] (rf))
      ([system] (rf system))
      ([system {:keys [name ow.system/request-listener] :as component}]
       (let [component (if request-listener
                         (update-in component [:ow.system/request-listener :in-mult] #(or % in-mult))
                         component)
             component (update-in component [:ow.system/requester :out-ch] #(or % out-ch))
             system    (assoc-in system [:components name] component)]
         (rf system component))))))

(defn init-lifecycle-xf [rf]
  (letfn [(run-loop [{{:keys [handler]} :ow.system/request-listener :as this} in-ch]
            (a/go-loop [{:keys [request response-ch] :as request-map} (a/<! in-ch)]
              (if-not (nil? request-map)
                (do (binding [*request-map* request-map]
                      #_(trace-request "received request-map" this)
                      (future
                        (let [handle-exception (fn handle-exception [e]
                                                 (log/debug "FAILED to invoke handler"
                                                            {:error-message (str e)
                                                             :trace-info    (trace-info this)}))
                              response (try
                                         (trace-request "invoking handler" this)
                                         (handler this request)
                                         (catch Exception e
                                           (handle-exception e)
                                           e)
                                         (catch Error e
                                           (handle-exception e)
                                           e))]
                          (cond
                            response-ch                    (do (trace-request "sending back handler's response" this)
                                                               (a/put! response-ch response))
                            (instance? Throwable response) (do (trace-request "throwing handler's exception" this)
                                                               (throw response))
                            true                           (do (trace-request  "discarding handler's response" this)
                                                               response)))))
                    (recur (a/<! in-ch))))))]

    (let [lifecycle {:start (fn [{{:keys [topic-fn topic in-mult]} :ow.system/request-listener :as this}]
                              (let [in-ch  (a/tap in-mult (a/chan))
                                    in-pub (a/pub in-ch (or topic-fn :topic))
                                    in-sub (a/sub in-pub topic (a/chan))]
                                (run-loop this in-sub)
                                (assoc-in this [:ow.system/request-listener :in-ch] in-ch)))
                     :stop (fn [this]
                             (update-in this [:ow.system/request-listener :in-ch] #(and % a/close! nil)))}]

      (fn
        ([] (rf))
        ([system] (rf system))
        ([system {:keys [name ow.system/instance ow.system/request-listener] :as component}]
         (let [component (if request-listener
                           (update-in component [:ow.system/lifecycles] conj lifecycle)
                           component)]
           (rf (assoc-in system [:components name instance] component) component)))))))

(defn emit [{:keys [ow.system/requester] :as this} topic request]
  (let [{:keys [out-ch]} requester
        event-map {:id      (rand-int Integer/MAX_VALUE)
                   :flowid  (get *request-map* :flowid
                                 (rand-int Integer/MAX_VALUE))
                   :topic   topic
                   :request request}]
    (binding [*request-map* event-map]
      (trace-request "emitting event-map"))
    (a/put! out-ch event-map)))

(defn request [{:keys [ow.system/requester] :as this} topic request & {:keys [timeout]}]
  (let [{:keys [out-ch]} requester
        response-ch (a/promise-chan)
        request-map {:id          (rand-int Integer/MAX_VALUE)
                     :flowid      (get *request-map* :flowid
                                       (rand-int Integer/MAX_VALUE))
                     :topic       topic
                     :request     request
                     :response-ch response-ch}
        receipt     (a/go
                      (let [timeout-ch    (a/timeout (or timeout 30000))
                            [response ch] (a/alts! [response-ch timeout-ch])]
                        (if (= ch response-ch)
                          (if-not (nil? response)
                            response
                            (ex-info "response channel was closed" {:trace-info (trace-info)
                                                                    :request request}))
                          (ex-info "timeout while waiting for response" {:trace-info (trace-info)
                                                                         :request request}))))]
    (binding [*request-map* request-map]
      (trace-request "requesting request-map")
      (a/put! out-ch request-map)
      (let [response (a/<!! receipt)]
        (trace-request "received response")
        (if-not (instance? Throwable response)
          response
          (throw response))))))
