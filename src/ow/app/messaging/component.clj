(ns ow.app.messaging.component)

(defprotocol Component
  (get-dispatch-map [this]))
