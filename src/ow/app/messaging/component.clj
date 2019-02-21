(ns ow.app.messaging.component)

(defprotocol Component
  (get-dispatch-map [this]))

(defn validate-components [components]
  (letfn [(validate-component [c]
            (satisfies? Component c))]
    (doseq [c components]
      (assert (validate-component c) (format "Component protocol implementation missing for %s" (type c))))))
