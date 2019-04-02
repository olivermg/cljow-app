(ns ow.comm
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

;;; TODO: can we generalize the concept of tracing request-/invocation-chains for logging?

(def ^:private ^:dynamic *request-map* nil)

(defn- trace-request [msg]
  (log/trace (select-keys *request-map* #{:id :flowid :topic}) msg))



(defn construct [name in-ch handler]
  (owl/construct ::comm name {::in-ch in-ch
                              ::handler handler}))

(defmethod owl/start* ::comm [{:keys [::in-ch ::handler ::pipe] :as this}]
  (if-not pipe
    (let [pipe (a/pipe in-ch (a/chan))]
      (a/go-loop [{:keys [request response-ch] :as request-map} (a/<! pipe)]
        (if-not (nil? request-map)
          (do (binding [*request-map* request-map]
                (trace-request "received request-map")
                (future
                  (let [response (try
                                   (trace-request "invoking handler")
                                   (handler this request)
                                   (catch Exception e
                                     (log/debug "Handler threw Exception" e)
                                     e)
                                   (catch Error e
                                     (log/debug "Handler threw Error" e)
                                     e))]
                    (cond
                      response-ch                    (do (trace-request "sending back handler's response")
                                                         (a/put! response-ch response))
                      (instance? Throwable response) (do (trace-request "throwing handler's exception")
                                                         (throw response))
                      true                           (do (trace-request "discarding handler's response")
                                                         response)))))
              (recur (a/<! pipe)))))
      (assoc this ::pipe pipe))
    this))

(defmethod owl/stop* ::comm [{:keys [::pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this ::pipe nil))

(defn emit [out-ch request & {:keys [topic]}]
  (let [event-map {:id      (rand-int Integer/MAX_VALUE)
                   :flowid  (get *request-map* :flowid (rand-int Integer/MAX_VALUE))
                   :topic   topic
                   :request request}]
    (binding [*request-map* event-map]
      (trace-request "emitting event-map"))
    (a/put! out-ch event-map)))

(defn request [out-ch request & {:keys [topic timeout]}]
  (let [response-ch (a/promise-chan)
        request-map {:id          (rand-int Integer/MAX_VALUE)
                     :flowid      (get *request-map* :flowid (rand-int Integer/MAX_VALUE))
                     :topic       topic
                     :request     request
                     :response-ch response-ch}
        receipt     (a/go
                      (let [timeout-ch    (a/timeout (or timeout 30000))
                            [response ch] (a/alts! [response-ch timeout-ch])]
                        (if (= ch response-ch)
                          (if-not (nil? response)
                            response
                            (ex-info "response channel was closed" {:request request}))
                          (ex-info "timeout while waiting for response" {:request request}))))]
    (binding [*request-map* request-map]
      (trace-request "requesting request-map")
      (a/put! out-ch request-map)
      (let [response (a/<!! receipt)]
        (trace-request "received response")
        (if-not (instance? Throwable response)
          response
          (throw response))))))

(def topic-fn :topic)



#_(let [in     (a/chan)
      out    (a/chan)
      c1     (-> (construct in (fn [this req]
                                 (println "c1" req)
                                 (->> (assoc req :c1 :c1)
                                      (request out))))
                 owl/start)
      c2     (-> (construct out (fn [this req]
                                  (println "c2" req)
                                  (assoc req :c2 :c2)))
                 owl/start)]
  (doto (request in {} :timeout 5000)
    (println "<-RESULT"))
  (Thread/sleep 1000)
  (owl/stop c2)
  (owl/stop c1))
