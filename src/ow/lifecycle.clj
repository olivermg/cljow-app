(ns ow.lifecycle
  (:require [clojure.tools.logging :as log]))

(defn construct [type name data]
  (assoc data
         ::type type
         ::name name))

(defn get-type [{:keys [::type] :as this}]
  type)

(defn get-name [{:keys [::name] :as this}]
  name)

(defmulti start* (fn [{:keys [::type] :as this} & args] type))
(defmulti stop* (fn [{:keys [::type] :as this} & args] type))

(defmethod start* :default [this] this)
(defmethod stop* :default [this] this)

(defn start [this]
  (log/info (str "Starting " (get-name this) "[" (get-type this) "]"))
  (start* this))

(defn stop [this]
  (log/info (str "Stopping " (get-name this) "[" (get-type this) "]"))
  (stop* this))
