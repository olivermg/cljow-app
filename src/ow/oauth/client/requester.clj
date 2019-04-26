(ns ow.oauth.client.requester)

(defprotocol OAuthRequester
  (grant-via-authorization-code [this code])
  (refresh [this refresh-token])
  (request [this access-token method path headers body]))
