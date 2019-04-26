(ns ow.oauth.client
  (:require [clojure.tools.logging :as log]
            [ow.clojure :as owclj]
            [ow.oauth.client.requester :as oocr]
            [ow.oauth.client.token-storage :as oocts]))

(defrecord OAuthClient [token-storage oauth-requester])

(defn grant [{:keys [token-storage oauth-requester] :as this} token-id code scopes]
  (let [{:keys [access-token refresh-token expires-at] :as oauth-token} (oocr/grant-via-authorization-code oauth-requester code)]
    (oocts/set-token token-storage token-id access-token refresh-token expires-at)
    access-token))

(defn request [{:keys [token-storage oauth-requester] :as this} token-id method path & {:keys [headers body]}]
  (letfn [(has-expired? [expires-at]
            false)

          (expires-soon? [expires-at]
            false)

          (refresh [refresh-token]
            (owclj/tries [5
                          :try-sym       try
                          :exception-f   (fn [e try]
                                           (log/warn "refresh failed" try (str e)))
                          :retry-delay-f (owclj/make-retry-delay-log10-f :exp 1.5 :cap (* 60 1000) :varpct 10.0)]
                         (log/trace "refresh, try" try)
                         (let [{:keys [access-token refresh-token expires-at] :as oauth-token} (oocr/refresh oauth-requester refresh-token)]
                           (oocts/set-token token-storage token-id access-token refresh-token expires-at)
                           access-token)))

          (refresh-async [refresh-token]
            (future
              (refresh refresh-token)))

          (request [access-token refresh-token]
            (let [{:keys [status] :as result} (oocr/request oauth-requester access-token method path headers body)]
              (if-not (= status 401)
                result
                (let [access-token (refresh refresh-token)]
                  (oocr/request oauth-requester access-token method path headers body)))))]

    (let [{:keys [access-token refresh-token expires-at] :as oauth-token} (oocts/get-token token-storage token-id)
          access-token (or (cond
                             (has-expired? expires-at)  (refresh refresh-token)
                             (expires-soon? expires-at) (do (refresh-async refresh-token) nil))
                           access-token)]
      (request access-token refresh-token))))
