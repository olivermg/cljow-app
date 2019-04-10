(ns ow.system.dependencies
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ow.system :as ows]
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
    (let [dags-count         (count (uga/connected-components dags))
          roots              (take dags-count (uga/topsort dags))
          start-orders       (->> roots
                                  (map #(uga/post-traverse dags %))
                                  (into #{}))
          missing-components (->> (set/difference (set (ug/nodes dags))
                                                  (set (apply concat start-orders)))
                                  (map vector))
          start-orders       (apply conj start-orders missing-components)]
      (assoc system :start-orders start-orders))))
