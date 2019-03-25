(ns ow.comm.events.receiver
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [ch handler]
  (owl/construct ::receiver {:ch ch
                             :handler handler}))

(defmethod owl/start ::receiver [{:keys [ch handler] :as this}]
  (let [pipe (a/pipe ch (a/chan))]
    (a/go-loop [event (a/<! pipe)]
      (if-not (nil? event)
        (do (future
              (try
                (handler this event)
                (catch Exception e
                  (log/debug "Handler threw Exception" e)
                  (throw e))
                (catch Error e
                  (log/debug "Handler threw Error" e)
                  (throw e))))
            (recur (a/<! pipe)))
        (log/info "Stopping event receiver" ::receiver)))
    (assoc this ::pipe pipe)))

(defmethod owl/stop ::receiver [{:keys [::pipe] :as this}]
  (when pipe
    (a/close! pipe))
  (assoc this ::pipe nil))
