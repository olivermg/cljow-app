(ns ow.system.lifecycles
  (:require [clojure.tools.logging :as log]))

(letfn [(start-or-stop-component [{:keys [lifecycles ::prev-lifecycle-op] :as component} op-kw]
          (if-not (= op-kw prev-lifecycle-op)
            (reduce (fn [component lifecycle]
                      (let [f (get lifecycle op-kw identity)]
                        (-> (f component)
                            (assoc ::prev-lifecycle-op op-kw))))
                    component
                    lifecycles)
            component))]

  (defn make-start-or-stop-xf [op-kw]
    (assert (#{:start :stop} op-kw))
    (fn [xf]
      (fn
        ([]
         (xf))
        ([system]
         (xf system))
        ([system {:keys [name] :as component}]
         (let [started-component (start-or-stop-component component op-kw)]
           (xf (assoc-in system [:components name] started-component) started-component)))))))
