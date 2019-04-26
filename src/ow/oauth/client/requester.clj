(ns ow.oauth.client.requester)

(defprotocol OAuthRequester
  (do-grant [this code scopes])
  (do-refresh [this refresh-token])
  (do-request [this access-token method path headers body]))
