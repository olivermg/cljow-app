(ns ow.app.message-broker.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.emitter :as owme]))

(defrecord CoreAsyncEmitter [in out]

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this)

  owme/Emitter

  (emit [this type data]
    )

  (wait [this receipt]
    ))

(defn core-async-emitter [in out]
  (map->CoreAsyncEmitter {:in  in
                          :out out}))
