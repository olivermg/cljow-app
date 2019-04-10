(ns ow.system.request-listener
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.system :as ows]))

(def ^:private ^:dynamic *request-map* {})

(defn- trace-info [& [this]]
  (-> (select-keys *request-map* #{:id :flowid})
      (assoc :name (or (and this (:name this))
                       "(n/a)"))))

(defn- trace-request [msg & [this]]
  (log/trace (trace-info this) msg))

(defn init-system [system]
  (let [out-ch  (a/chan)
        in-mult (a/mult out-ch)]
    (reduce (fn [system [name {:keys [request-listener] :as this}]]
              (if request-listener
                (-> system
                    (update-in [name :request-listener :in-mult] #(or % in-mult))
                    (update-in [name :request-listener :out-ch] #(or % out-ch)))
                system))
            system
            system)))

(defn init-component [{:keys [request-listener] :as this}]
  (if request-listener
    (let [{:keys [in-mult out-ch]} request-listener
          lifecycle {:start (fn [this]
                              (let [in-ch (a/tap in-mult (a/chan))]
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
                                                             (request-listener this request)
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
                                        (recur (a/<! in-ch)))))
                                (assoc-in this [:request-listener :in-ch] in-ch)))
                     :stop (fn [this]
                             (update-in this [:request-listener :in-ch] #(and % a/close! nil)))}]
      (update-in this [:lifecycles] conj lifecycle))
    this))
