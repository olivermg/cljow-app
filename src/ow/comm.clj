(ns ow.comm
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [in-ch handler]
  (owl/construct ::comm {::in-ch in-ch
                         ::handler handler}))

(defmethod owl/start* ::comm [{:keys [::in-ch ::handler ::pipe] :as this}]
  (if-not pipe
    (let [pipe (a/pipe in-ch (a/chan))]
      (a/go-loop [{:keys [request response-ch] :as request-map} (a/<! pipe)]
        (if-not (nil? request-map)
          (do (future
                (let [response (try
                                 (handler this request)
                                 (catch Exception e
                                   (log/debug "Handler threw Exception" e)
                                   e)
                                 (catch Error e
                                   (log/debug "Handler threw Error" e)
                                   e))]
                  (cond
                    response-ch                    (a/put! response-ch response)
                    (instance? Throwable response) (throw response)
                    true                           response)))
              (recur (a/<! pipe)))
          (log/info "Stopped comm" (owl/get-type this))))
      (log/info "Started comm" (owl/get-type this))
      (assoc this ::pipe pipe))
    this))

(defmethod owl/stop* ::comm [{:keys [::pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this ::pipe nil))

(defn emit [out-ch request & {:keys [topic]}]
  (a/put! out-ch {:topic   topic
                  :request request}))

(defn request [out-ch request & {:keys [topic timeout]}]
  (let [response-ch (a/promise-chan)
        request-map {:topic       topic
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
    (a/put! out-ch request-map)
    (let [response (a/<!! receipt)]
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
