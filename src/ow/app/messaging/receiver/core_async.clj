(ns ow.app.messaging.receiver.core-async
  (:require [ow.app.lifecycle :as owl]
            [ow.app.messaging.receiver :as owmr]))

(defrecord CoreAsyncReceiver [in out components]

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this)

  owmr/Receiver)

(defn core-async-receiver [in out components]
  (map->CoreAsyncReceiver {:in  in
                           :out out
                           :components components}))
