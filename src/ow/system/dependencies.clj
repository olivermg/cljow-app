(ns ow.system.dependencies
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ubergraph.core :as ug]
            [ubergraph.alg :as uga]))

(defn init-system [{:keys [components] :as system}]
  (let [dags (reduce (fn [graph [component-name {:keys [dependencies] :as component}]]
                       (let [graph (ug/add-nodes graph component-name)]
                         (->> dependencies
                              (map (fn [d] [component-name d]))
                              (apply ug/add-edges graph))))
                     (ug/digraph)
                     components)]

    (when-not (-> dags uga/connect uga/dag?)
      (throw (ex-info "circular dependencies detected" {:dependency-graph (with-out-str (ug/pprint dags))})))

    (let [start-order        (-> dags uga/connect uga/post-traverse)
          missing-components (set/difference (set (ug/nodes dags))
                                             (set start-order))
          start-order        (apply conj start-order missing-components)]
      (assoc system :start-order start-order))))
