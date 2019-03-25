(ns ow.comm.request-response.responder
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [in-ch out-ch handler]
  (owl/construct ::responder {:in-ch in-ch
                              :out-ch out-ch
                              :handler handler}))

(defmethod owl/start ::responder [{:keys [in-ch out-ch handler] :as this}]
  (let [pipe (a/pipe in-ch (a/chan))]
    (a/go-loop [request (a/<! pipe)]
      (if-not (nil? request)
        (do (future
              (let [response (try
                               (handler this request)
                               (catch Exception e
                                 (log/debug "Handler threw Exception" e)
                                 e)
                               (catch Error e
                                 (log/debug "Handler threw Error" e)
                                 e))]
                (a/put! out-ch response)))
            (recur (a/<! pipe)))
        (log/info "Stopped responder" ::responder)))))

(defmethod owl/stop ::responder [{:keys [pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this :pipe nil))
