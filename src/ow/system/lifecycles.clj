(ns ow.system.lifecycles
  (:require [clojure.tools.logging :as log]
            [ow.system :as ows]))

(defn init-component [name {:keys [lifecycles] :as definition}]
  (letfn [(init-lifecycle [{:keys [start stop] :as lifecycles}]
            )]

    (reduce (fn [s lifecycle]
              (conj s (init-lifecycle lifecycle)))
            []
            lifecycles)))

(defn init-system [system]
  )
