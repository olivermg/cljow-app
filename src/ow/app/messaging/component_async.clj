(ns ow.app.messaging.component-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defrecord Component [name in-ch out-ch handler
                      in-pipe]

  owl/Lifecycle

  (start [this]
    (if-not in-pipe
      (let [in-pipe (a/pipe in-ch (a/chan))]
        (log/info "Starting" name)
        (a/go-loop [msg (a/<! in-pipe)]
          (if-not (nil? msg)
            (do (future
                  (handler this msg))
                (recur (a/<! in-pipe)))
            (log/info "Stopped" name)))
        (assoc this :in-pipe in-pipe))
      this))

  (stop [this]
    (when in-pipe
      (a/close! in-pipe))
    (assoc this :in-pipe nil)))

(defn component [name in-ch out-ch handler]
  (map->Component {:name name
                   :in-ch in-ch
                   :out-ch out-ch
                   :handler handler}))
