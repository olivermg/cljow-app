(ns ow.comm
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [name in-ch handler]
  (owl/construct ::comm name {::in-ch in-ch
                              ::handler handler}))

(defmethod owl/start* ::comm [{:keys [::in-ch ::handler ::pipe] :as this}]
  (if-not pipe
    (let [pipe (a/pipe in-ch (a/chan))]
      (a/go-loop [{:keys [request response-ch] :as request-map} (a/<! pipe)]
        (let [log-msg (select-keys request-map #{:id :flowid :topic})]
          (log/trace "received request-map" log-msg)
          (if-not (nil? request-map)
            (do (future
                  (let [response (try
                                   (log/trace "invoking handler" log-msg)
                                   (handler this request)
                                   (catch Exception e
                                     (log/debug "Handler threw Exception" e)
                                     e)
                                   (catch Error e
                                     (log/debug "Handler threw Error" e)
                                     e))]
                    (cond
                      response-ch                    (do (log/trace "sending back handler's response" log-msg)
                                                         (a/put! response-ch response))
                      (instance? Throwable response) (do (log/trace "throwing handler's exception" log-msg)
                                                         (throw response))
                      true                           (do (log/trace "discarding handler's response" log-msg)
                                                         response))))
                (recur (a/<! pipe))))))
      (assoc this ::pipe pipe))
    this))

(defmethod owl/stop* ::comm [{:keys [::pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this ::pipe nil))

(defn emit [out-ch request & {:keys [topic parent-event]}]
  (let [event-map {:id      (rand-int Integer/MAX_VALUE)
                   :flowid  (get parent-event :flowid (rand-int Integer/MAX_VALUE))
                   :topic   topic
                   :request request}]
    (log/trace "emitting event-map" (select-keys event-map #{:id :flowid :topic}))
    (a/put! out-ch event-map)))

(defn request [out-ch request & {:keys [topic timeout parent-request]}]
  (let [response-ch (a/promise-chan)
        request-map {:id          (rand-int Integer/MAX_VALUE)
                     :flowid      (get parent-request :flowid (rand-int Integer/MAX_VALUE))
                     :topic       topic
                     :request     request
                     :response-ch response-ch}
        log-msg     (select-keys request-map #{:id :flowid :topic})
        receipt     (a/go
                      (let [timeout-ch    (a/timeout (or timeout 30000))
                            [response ch] (a/alts! [response-ch timeout-ch])]
                        (if (= ch response-ch)
                          (if-not (nil? response)
                            response
                            (ex-info "response channel was closed" {:request request}))
                          (ex-info "timeout while waiting for response" {:request request}))))]
    (log/trace "requesting request-map" log-msg)
    (a/put! out-ch request-map)
    (let [response (a/<!! receipt)]
      (log/trace "received response" log-msg)
      (if-not (instance? Throwable response)
        response
        (throw response)))))

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
