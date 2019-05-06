(ns ow.oauth.client.requester.heroku-platform-api
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [ow.http-client :as ohc]
            [ow.lifecycle :as ol]
            [ow.oauth.client.requester :as oocr]))

(defn- handle-http-response [{:keys [status error body] :as response}]
  (when error
    (throw (ex-info "http-request failed with error" {:error error})))
  (update response :body #(if-not (empty? %) (json/read-str % :key-fn keyword) %)))

(defrecord HerokuPartnerApiOAuthRequester [client-secret base-url
                                           http-client]

  oocr/OAuthRequester

  (grant-via-authorization-code [this code]
    (let [{{:keys [access_token refresh_token expires_in token_type] :as body} :body :as response}
          (-> @(http/post "https://id.heroku.com/oauth/token"
                          {:client http-client
                           :form-params {:grant_type    "authorization_code"
                                         :code          code
                                         :client_secret client-secret}})
              (handle-http-response))]
      (log/trace "heroku grant response received" body)
      {:access-token  access_token
       :refresh-token refresh_token
       :expires-at    (->> (- (or expires_in 0) 30)
                           (t/seconds)
                           (t/plus (t/now))
                           tc/to-date)
       :type          token_type}))

  (refresh [this {:keys [refresh-token] :as oauth-token}]
    )

  (request [this {:keys [type access-token] :as oauth-token} method path headers body]
    (-> @(http/request {:url     (str base-url path)
                        :method  method
                        :client  http-client
                        :timeout 10000
                        :headers (merge (into {} [(when body
                                                    ["content-type"  "application/json"])
                                                  ["accept"        "application/vnd.heroku+json; version=3"]
                                                  ["authorization" (str type " " access-token)]])
                                        headers)
                        :body    (when body
                                   (json/write-str body))})
        (handle-http-response)
        (select-keys #{:status :body}))))

(defmethod ol/start* HerokuPartnerApiOAuthRequester [this dependencies]
  (merge this
         dependencies
         {:http-client (ohc/make-client)}))

(defmethod ol/stop* HerokuPartnerApiOAuthRequester [this]
  {:dependencies {}
   :this         (assoc this :http-client nil)})

(defn construct [client-secret]
  (map->HerokuPartnerApiOAuthRequester {:client-secret client-secret
                                        :base-url      "https://api.heroku.com"}))
