(ns ow.app.lifecycle)

(defprotocol Lifecycle
  (start* [this parent])
  (stop* [this parent]))

(defn start [this]
  (let [this (reduce (fn [t [k _]]
                       (if (= (-> k name keyword) :this)
                         (update t k #(when % (start* % t)))
                         t))
                     this
                     this)]
    (start* this nil)))

(defn stop [this]
  (let [this (stop* this nil)]
    (reduce (fn [t [k _]]
              (if (= (-> k name keyword) :this)
                (update t k #(when % (stop* % t)))
                t))
            this
            (reverse this))))
