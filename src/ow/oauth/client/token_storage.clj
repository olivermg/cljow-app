(ns ow.oauth.client.token-storage)

(defprotocol TokenStorage
  (get-token [this id])
  (set-token [this id token-data]))
