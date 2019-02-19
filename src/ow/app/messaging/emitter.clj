(ns ow.app.messaging.emitter)

(defprotocol Emitter
  (emit [this type data])
  (wait [this receipt]))
