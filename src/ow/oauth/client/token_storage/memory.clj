(ns ow.oauth.client.token-storage.memory
  (:require [clojure.tools.logging :as log]
            [ow.lifecycle :as ol]
            [ow.oauth.client.token-storage :as oocts]))

(defonce ^:private +storage+ (atom {}))

(defrecord MemoryTokenStorage []

  oocts/TokenStorage

  (get-token [this id]
    (get @+storage+ id))

  (set-token [this id token-data]
    (swap! +storage+ merge token-data
           #_{:type          type
            :access-token  access-token
            :refresh-token refresh-token
            :expires-at    expires-at})))

(defmethod ol/start* MemoryTokenStorage [this dependencies]
  (merge this dependencies))

(defmethod ol/stop* MemoryTokenStorage [this]
  {:dependencies {}
   :this         this})

(defn construct []
  (map->MemoryTokenStorage {}))
