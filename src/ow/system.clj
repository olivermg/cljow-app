(ns ow.system
  (:require [clojure.tools.logging :as log]))

;;; example for definition:
#_{:component1 (fn [this request]
               (log/debug "component1 handler received request:" request)
               {:foo "bar1"})

 :component2 {:handler      (fn [this request]
                              (log/debug "component2 handler received request:" request)
                              {:foo "bar2"})
              :config       {:port 8080}
              :dependencies #{:component3}}

 :component3 {:construct (fn [config]
                           {:bla :blub
                            :moo nil})
              :start     (fn [this]
                           (assoc this :moo 123))
              :stop      (fn [this]
                           (assoc this :moo nil))}}

(defmulti init (fn [[k v] m] k))

(defmethod init :handler [[_ v] m]
  v)

(defmethod init :config [[_ v] m]
  v)

(defmethod init :dependencies [[_ v] m]
  v)

(defmethod init :construct [[_ v] m]
  (or v (fn [config]
          {})))

(defmethod init :start [[_ v] m]
  (or v identity))

(defmethod init :stop [[_ v] m]
  (or v identity))

(defn make-system [definitions]
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
