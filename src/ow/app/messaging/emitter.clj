(ns ow.app.messaging.emitter)

(defprotocol Emitter
  (emit [this type data])
  (recv [this receipt]))

(defn emit-sync [this type data]
  (->> (emit this type data)
       (recv this)))
