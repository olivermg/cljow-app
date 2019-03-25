(ns ow.lifecycle
  (:require [clojure.tools.logging :as log]))

(defn construct [type data]
  (assoc data ::type type))

(defmulti start (fn [{:keys [::type] :as this} & args] type))
(defmulti stop (fn [{:keys [::type] :as this} & args] type))

(defmethod start :default [this] this)
(defmethod stop :default [this] this)
