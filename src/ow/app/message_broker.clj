(ns ow.app.message-broker)

(defprotocol MessageBroker
  (emit [this type data])
  (wait [this receipt]))

(defprotocol MessageComponent
  (get-dispatch-map [this]))
