(ns ow.system.request-listener
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(def ^:private ^:dynamic *request-map* {})

(defn- trace-info [this]
  (-> (select-keys *request-map* #{:id :flowid})
      (assoc :name (:ow.system/name this))))

(defn- trace-request [this msg]
  (log/trace (trace-info this) msg))

(defn init-request-response-channels-xf [rf]
  (let [out-ch  (a/chan)
        in-mult (a/mult out-ch)]
    (fn
      ([] (rf))
      ([system] (rf system))
      ([system {:keys [ow.system/name ow.system/instance ow.system/request-listener] :as component}]
       (let [system    (if request-listener
                         (let [{:keys [topic-fn topic]} request-listener]
                           (update-in system [:components name :worker-sub]
                                      #(or % (let [in-tap (a/tap in-mult (a/chan))
                                                   in-pub (a/pub in-tap (or topic-fn :topic))]
                                               (a/sub in-pub topic (a/chan))))))
                         system)
             component (update-in component [:ow.system/requester :out-ch] #(or % out-ch))
             system    (assoc-in system [:components name :workers instance] component)]
         (rf system component))))))

(defn init-lifecycle-xf [rf]
  (letfn [(run-loop [{{:keys [handler]} :ow.system/request-listener :as this} in-ch]
            (a/go-loop [{:keys [request response-ch] :as request-map} (a/<! in-ch)]
              (if-not (nil? request-map)
                (do (binding [*request-map* request-map]
                      #_(trace-request this "received request-map")
                      (future
                        (let [handle-exception (fn handle-exception [e]
                                                 (log/debug "FAILED to invoke handler"
                                                            {:error-message (str e)
                                                             :trace-info    (trace-info this)}))
                              response (try
                                         (trace-request this "invoking handler")
                                         (handler this request)
                                         (catch Exception e
                                           (handle-exception e)
                                           e)
                                         (catch Error e
                                           (handle-exception e)
                                           e))]
                          (cond
                            response-ch                    (do (trace-request this "sending back handler's response")
                                                               (a/put! response-ch response))
                            (instance? Throwable response) (do (trace-request this "throwing handler's exception")
                                                               (throw response))
                            true                           (do (trace-request this "discarding handler's response")
                                                               response)))))
                    (recur (a/<! in-ch))))))

          (make-lifecycle [system component-name]
            (let [worker-sub (get-in system [:components component-name :worker-sub])]
              {:start (fn request-listener-start [{{:keys [topic-fn topic]} :ow.system/request-listener :as this}]
                        (let [in-ch (a/pipe worker-sub (a/chan))]
                          (run-loop this in-ch)
                          (assoc-in this [:ow.system/request-listener :in-ch] in-ch)))
               :stop (fn request-listener-stop [this]
                       (update-in this [:ow.system/request-listener :in-ch] #(and % a/close! nil)))}))]

    (fn
      ([] (rf))
      ([system] (rf system))
      ([system {:keys [ow.system/name ow.system/instance ow.system/request-listener] :as component}]
       (let [component (if request-listener
                         (update-in component [:ow.system/lifecycles] conj (make-lifecycle system name))
                         component)]
         (rf (assoc-in system [:components name :workers instance] component) component))))))

(defn emit [{:keys [ow.system/requester] :as this} topic request]
  (let [{:keys [out-ch]} requester
        event-map {:id      (rand-int Integer/MAX_VALUE)
                   :flowid  (get *request-map* :flowid
                                 (rand-int Integer/MAX_VALUE))
                   :topic   topic
                   :request request}]
    (binding [*request-map* event-map]
      (trace-request this "emitting event-map"))
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
                            (ex-info "response channel was closed" {:trace-info (trace-info this)
                                                                    :request request}))
                          (ex-info "timeout while waiting for response" {:trace-info (trace-info this)
                                                                         :request request}))))]
    (binding [*request-map* request-map]
      (trace-request this "requesting request-map")
      (a/put! out-ch request-map)
      (let [response (a/<!! receipt)]
        (trace-request this "received response")
        (if-not (instance? Throwable response)
          response
          (throw response))))))
