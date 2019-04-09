(ns ow.system.dependencies
  (:require [clojure.tools.logging :as log]
            [ow.system :as ows]
            [ubergraph.core :as ug]
            [ubergraph.alg :as uga]))

(defn init [{:keys [definition] :as system}]
  (let [dags (reduce (fn [graph [component-name {:keys [dependencies] :as component-definition}]]
                       (let [graph (ug/add-nodes graph component-name)]
                         (->> dependencies
                              (map (fn [d] [component-name d]))
                              (apply ug/add-edges graph))))
                     (ug/digraph)
                     definition)]
    (when-not (-> dags uga/connect uga/dag?)
      (throw (ex-info "circular dependencies detected" {:dependency-graph (with-out-str (ug/pprint dags))})))
    (let [dags-count (count (uga/connected-components dags))
          roots      (take dags-count (uga/topsort dags))
          start-order (->> roots
                           (map #(uga/post-traverse dags %))
                           (into #{}))]
      (assoc system :start-order start-order))))
