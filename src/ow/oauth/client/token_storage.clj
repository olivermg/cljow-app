(ns ow.oauth.client.token-storage)

(defprotocol TokenStorage
  (get-token [this id])
  (set-token [this id access-token refresh-token expires-at]))
