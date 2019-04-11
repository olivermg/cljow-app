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


(letfn [(inject-dependencies [system {:keys [::prev-dependency-op] :as component}]
          (if-not (= prev-dependency-op :inject)
            (-> (update component :dependencies
                        #(->> (map (fn [depcn]
                                     [depcn (get-in system [:components depcn])])
                                   %)
                              (into {})))
                (assoc ::prev-dependency-op :inject))
            component))

        (deject-dependencies [_ {:keys [::prev-dependency-op] :as component}]
          (if-not (= prev-dependency-op :deject)
            (-> (update component :dependencies
                        #(-> (map (fn [[depcn _]]
                                    depcn)
                                  %)
                             set))
                (assoc ::prev-dependency-op :deject))
            component))]

  (defn make-inject-or-deject-dependencies-xf [op-kw]
    (assert (#{:inject :deject} op-kw))
    (let [op (case op-kw
               :inject inject-dependencies
               :deject deject-dependencies)]
      (fn [xf]
        (fn
          ([]
           (xf))
          ([system]
           (xf system))
          ([system {:keys [name] :as component}]
           (let [resulting-component (op system component)]
             (xf (assoc-in system [:components name] resulting-component) resulting-component))))))))
