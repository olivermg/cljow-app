(ns ow.oauth.client.token-storage.memory
  (:require #_[clojure.tools.logging :as log]
            [ow.logging.api.alpha :as log]
            [ow.lifecycle :as ol]
            [ow.oauth.client.token-storage :as oocts]))

(defonce ^:private +storage+ (atom {}))

(defrecord MemoryTokenStorage []

  oocts/TokenStorage

  (get-token [this id]
    (let [token (get @+storage+ id)]
      (log/trace "GET TOKEN" {:id id :token token})
      token))

  (set-token [this id token-data]
    (log/trace "STORE TOKEN" {:id id :token-data token-data})
    (swap! +storage+ update id #(merge % token-data))))

(defmethod ol/start* MemoryTokenStorage [this dependencies]
  (merge this dependencies))

(defmethod ol/stop* MemoryTokenStorage [this]
  {:dependencies {}
   :this         this})

(defn construct []
  (map->MemoryTokenStorage {}))
