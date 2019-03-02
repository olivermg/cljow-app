(ns ow.app.messaging.bridge.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defrecord CoreAsyncBridge [from to
                            pipe]

  owl/Lifecycle

  (start [this]
    (if-not pipe
      (let [pipe (a/chan)]
        (a/pipe from pipe)
        (a/pipe pipe to)
        (assoc this :pipe pipe))
      this))

  (stop [this]
    (if pipe
      (do (a/close! pipe)
          (assoc this :pipe nil))
      this)))

(defn core-async-bridge [from to]
  (map->CoreAsyncBridge {:from from
                         :to to}))
