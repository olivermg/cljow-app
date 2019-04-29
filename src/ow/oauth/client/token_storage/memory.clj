(ns ow.oauth.client.token-storage.memory
  (:require [clojure.tools.logging :as log]
            [ow.lifecycle :as ol]
            [ow.oauth.client.token-storage :as oocts]))

(defonce ^:private +storage+ (atom {}))

(defrecord MemoryTokenStorage []

  oocts/TokenStorage

  (get-token [this id]
    (let [token (get @+storage+ id)]
      (log/trace "GET TOKEN" id token)
      token))

  (set-token [this id token-data]
    (log/trace "STORE TOKEN" id token-data)
    (swap! +storage+ update id #(merge % token-data))))

(defmethod ol/start* MemoryTokenStorage [this dependencies]
  (merge this dependencies))

(defmethod ol/stop* MemoryTokenStorage [this]
  {:dependencies {}
   :this         this})

(defn construct []
  (map->MemoryTokenStorage {}))
