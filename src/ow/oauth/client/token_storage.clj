(ns ow.oauth.client.token-storage
  (:refer-clojure :rename {update update-clj}))

(defprotocol TokenStorage
  (get-token [this id])
  (set-token [this id token-data]))

(defn update [this id token-data]
  )
