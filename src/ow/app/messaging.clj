(ns ow.app.messaging)

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
