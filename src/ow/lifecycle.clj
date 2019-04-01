(ns ow.lifecycle
  (:require [clojure.tools.logging :as log]))

(defn construct [type data]
  (assoc data ::type type))

(defn get-type [{:keys [::type] :as this}]
  type)

(defmulti start* (fn [{:keys [::type] :as this} & args] type))
(defmulti stop* (fn [{:keys [::type] :as this} & args] type))

(defmethod start* :default [this] this)
(defmethod stop* :default [this] this)

(defn start [this]
  (log/info "Starting" (get-type this))
  (start* this))

(defn stop [this]
  (log/info "Stopping" (get-type this))
  (stop* this))
