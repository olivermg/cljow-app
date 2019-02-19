(ns ow.app.message-broker.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]
            [ow.app.message-broker :as owm]))

(defrecord CoreAsync [in out]

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this)

  owm/MessageBroker

  (emit [this type data]
    )

  (wait [this receipt]
    ))

(defn core-async [in out]
  (map->CoreAsync {:in  in
                   :out out}))
