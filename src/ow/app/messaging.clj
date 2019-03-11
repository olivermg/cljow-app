(ns ow.app.messaging
  (:require [clojure.core.async :as a]))

(defn get-flow-id [message]
  (get message ::flow-id))

(defn get-topic [message]
  (get message ::topic))

(defn get-data [message]
  (get message ::data))

(defn message
  ([related-message topic data]
   {::topic topic
    ::flow-id (get-flow-id related-message)
    ::data data})
  ([topic data]
   {::topic topic
    ::flow-id (rand-int Integer/MAX_VALUE)
    ::data data}))

(defn put! [{:keys [out-ch] :as this} msg]
  (a/put! out-ch msg))
