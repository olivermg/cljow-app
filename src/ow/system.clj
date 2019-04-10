(ns ow.system
  (:require [clojure.tools.logging :as log]
            [ow.system.dependencies :as sd]
            [ow.system.lifecycle :as sl]))

;;; example for definition:
{:component1 {:request-listener {:topic :foo1
                                 :handler (fn [this request]
                                            (log/debug "component1 handler received request:" request)
                                            {:foo "bar1"})}}

 :component2 {:request-listener {:topic :foo2
                                 :handler (fn [this request]
                                            (log/debug "component2 handler received request:" request)
                                            {:foo "bar2"})}
              :config       {:port 8080}
              :dependencies #{:component3}}

 :component3 {:request-listener {:topic :foo3
                                 :handler (fn [this request]
                                            (log/debug "component3 handler received request:" request)
                                            {:foo "bar3"})}
              :lifecycles [{:start     (fn [this]
                                         (assoc this :moo 123))
                            :stop      (fn [this]
                                         (assoc this :moo nil))}]
              :config {:port 1234}}}

(defmulti init-component (fn [[component-name component-definition] system] component-name))

(defmethod init :handler [[_ v] m]
  v)

(defmethod init :config [[_ v] m]
  v)

#_(defmethod init :dependencies [[_ v] m]
  v)

(defmethod init :construct [[_ v] m]
  (or v (fn [config]
          {})))

#_(defmethod init :start [[_ v] m]
  (or v identity))

#_(defmethod init :stop [[_ v] m]
    (or v identity))

(defn init-system [definition]
  (let [init (comp )])
  (letfn [(normalize [definition]
            (let [definition (if (fn? definition)
                               {:handler definition})]
              (-> definition
                  (update :handler      #(or % identity))
                  (update :config       #(or % {}))
                  (update :dependencies #(or % #{}))
                  (update :construct    #(or % (fn [config]
                                                 {})))
                  (update :start        #(or % identity))
                  (update :stop         #(or % identity)))))]))

