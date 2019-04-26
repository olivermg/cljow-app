(ns ow.oauth.client.requester.heroku-partner-api
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [ow.http-client :as ohc]
            [ow.oauth.client.requester :as oocr]))

(defonce ^:private +urls+ {:grant "https://id.heroku.com/oauth/token"})

(defrecord HerokuPartnerApiOAuthRequester [client-secret
                                           http-client]

  oocr/OAuthRequester

  (grant-via-authorization-code [this code]
    (let [{:keys [status body]} @(http/post (get +urls+ :grant)
                                            {:client http-client
                                             :form-params {:grant_type    "authorization_code"
                                                           :code          code
                                                           :client_secret client-secret}})]
      (when-not (<= 200 status 299)
        (throw (ex-info "authorization-code grant responded with error" {:status status :body body})))
      (when (empty? body)
        (throw (ex-info "authorization-code grant responded with empty body" {:status status :body body})))
      (let [{:keys [access_token refresh_token expires_in token_type] :as body}
            (some-> body (json/read-str :key-fn keyword))]
        (when-not (and access_token refresh_token expires_in token_type)
          (throw (ex-info "missing data in authorization-code grant response" {:status status :body body})))
        {:access-token  access_token
         :refresh-token refresh_token
         :expires-at    (->> (- expires_in 30)
                             (t/seconds)
                             (t/plus (t/now))
                             tc/to-date)
         :type          token_type})))

  (refresh [this refresh-token]
    )

  (request [this access-token method path headers body]
    ))

(defn start [this]
  (assoc this :http-client (ohc/make-client)))

(defn stop [this]
  this)

(defn construct [client-secret]
  (map->HerokuPartnerApiOAuthRequester {:client-secret client-secret}))
