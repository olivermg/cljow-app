(ns ow.app.lifecycle)

(defprotocol Lifecycle
  (start [this])
  (stop [this]))
