(ns ow.comm.request-response
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]
            [ow.comm.request-response.requester :as req]
            [ow.comm.request-response.responder :as res]))

(defn construct [in-ch handler]
  (owl/construct ::requester-responder {:in-ch in-ch
                                        :handler handler}))

(defmethod owl/start ::requester-responder [{:keys [in-ch handler] :as this}]
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
                (a/put! response-ch response)))
            (recur (a/<! pipe)))
        (log/info "Stopped requester-responder")))
    (assoc this :pipe pipe)))

(defmethod owl/stop ::requester-responder [{:keys [pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this :pipe nil))

(defn request-async [out-ch request & {:keys [timeout]}]
  (let [response-ch (a/promise-chan)
        request-map {:request     request
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
    receipt))

(defn wait-for-response [receipt]
  (let [response (a/<!! receipt)]
    (if-not (instance? Throwable response)
      response
      (throw response))))

(defn request-sync [out-ch request & {:keys [timeout]}]
  (->> (request-async out-ch request :timeout timeout)
       (wait-for-response)))



#_(let [in     (a/chan)
      out    (a/chan)
      c1     (-> (construct in (fn [this request]
                                 (println "c1" this request)
                                 (->> (assoc request :c1 :c1)
                                      (request-sync out))))
                 owl/start)
      c2     (-> (construct out (fn [this request]
                                  (println "c2" this request)
                                  (assoc request :c2 :c2)))
                 owl/start)]
  (request-sync in {} :timeout 5000))
