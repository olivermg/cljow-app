(ns ow.oauth.client.token-storage.memory
  (:require [clojure.tools.logging :as log]
            [ow.oauth.client.token-storage :as oocts]))

(defonce ^:private +storage+ (atom {}))

(defrecord MemoryTokenStorage []

  oocts/TokenStorage

  (get-token [this id]
    (get @+storage+ id))

  (set-token [this id access-token refresh-token expires-at]
    (reset! +storage+ {:access-token access-token
                       :refresh-token refresh-token
                       :expires-at expires-at})))

(defn start [this]
  this)

(defn stop [this]
  this)

(defn construct []
  (map->MemoryTokenStorage {}))
