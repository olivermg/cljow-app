(ns ow.app.messaging.bridge.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defrecord CoreAsyncOutBridge [ch remote-ch
                               pipe]

  owl/Lifecycle

  (start [this]
    (if-not pipe
      (let [pipe (a/chan)]
        (a/pipe ch pipe)
        (a/pipe pipe remote-ch)
        (assoc this :pipe pipe))
      this))

  (stop [this]
    (if pipe
      (do (a/close! pipe)
          (assoc this :pipe nil))
      this)))

(defn core-async-out-bridge [ch remote-ch]
  (map->CoreAsyncOutBridge {:ch ch
                            :remote-ch remote-ch}))


(defrecord CoreAsyncInBridge [ch remote-ch
                              pipe]

  owl/Lifecycle

  (start [this]
    (if-not pipe
      (let [pipe (a/chan)]
        (a/pipe remote-ch pipe)
        (a/pipe pipe ch)
        (assoc this :pipe pipe))
      this))

  (stop [this]
    (if pipe
      (do (a/close! pipe)
          (assoc this :pipe nil))
      this)))

(defn core-async-in-bridge [ch remote-ch]
  (map->CoreAsyncInBridge {:ch ch
                           :remote-ch remote-ch}))
