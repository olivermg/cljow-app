(ns ow.system.lifecycles
  (:require [clojure.tools.logging :as log]))

(letfn [(start-or-stop-component [{:keys [lifecycles ::started?] :as component} op-kw]
          (let [started! (= op-kw :start)]
            (if-not (= started! started?)
              (reduce (fn [component lifecycle]
                        (let [f (get lifecycle op-kw identity)]
                          (-> (f component)
                              (assoc ::started? started!))))
                      component
                      lifecycles)
              component)))]

  (defn make-start-or-stop-xf [op-kw]
    (fn [xf]
      (fn
        ([]
         (xf))
        ([system]
         (xf system))
        ([system {:keys [name] :as component}]
         (let [started-component (start-or-stop-component component op-kw)]
           (xf (assoc-in system [:components name] started-component) started-component)))))))
