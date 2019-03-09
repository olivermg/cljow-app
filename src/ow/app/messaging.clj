(ns ow.app.messaging)

(defn get-id [message]
  (get message ::id))

(defn message
  ([related-message data]
   (assoc data ::id (get-id related-message)))
  ([data]
   (assoc data ::id (rand-int Integer/MAX_VALUE))))
