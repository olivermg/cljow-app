(ns ow.oauth.client
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            #_[clojure.tools.logging :as log]
            [ow.logging.api.alpha :as log]
            [ow.clojure :as owclj]
            [ow.lifecycle :as ol]
            [ow.oauth.client.requester :as oocr]
            [ow.oauth.client.token-storage :as oocts]))

(defrecord OAuthClient [token-storage oauth-requester])

(defn- token-expired? [{:keys [expires-at] :as oauth-token} & [date]]
  (t/after? (tc/to-date-time (or date (t/now)))
            (tc/to-date-time expires-at)))

(defn- check-token [{:keys [type access-token refresh-token expires-at] :as oauth-token}]
  (when-not (and type access-token refresh-token expires-at)  ;; TODO: check this via spec
    (throw (ex-info "oauth token is incomplete" {:oauth-token oauth-token})))
  (when (token-expired? oauth-token)
    (throw (ex-info "oauth token has expired" {:oauth-token oauth-token})))
  oauth-token)

(defn grant [{:keys [token-storage oauth-requester] :as this} token-id code]
  (log/trace "OAUTH GRANT" {:token-id token-id :code code})
  (let [oauth-token (-> (oocr/grant-via-authorization-code oauth-requester code)
                        (check-token))]
    (oocts/set-token token-storage token-id oauth-token)
    oauth-token))

(defn get-token [{:keys [token-storage] :as this} token-id]
  (log/trace "OAUTH GET-TOKEN" {:token-id token-id})
  (when-let [oauth-token (oocts/get-token token-storage token-id)]
    (when-not (token-expired? oauth-token)
      oauth-token)))

(defn request [{:keys [token-storage oauth-requester] :as this} token-id method path & {:keys [headers body]}]
  (log/trace "OAUTH REQUEST" {:tokenid token-id :method method :path path :headers headers :body body})
  (letfn [(trace [msg data]
            (log/trace msg (merge {:method      method
                                   :path        path}
                                  data)))

          (has-expired? [oauth-token]
            (token-expired? oauth-token))

          (expires-soon? [oauth-token]
            (token-expired? oauth-token (t/minus (t/now) (t/minutes 5))))

          (refresh-async [oauth-token]  ;; FIXME: implement protection for multiple overlapping refreshes
            (future
              (let [oauth-token (-> (oocr/refresh oauth-requester oauth-token)
                                    (check-token))]
                (oocts/set-token token-storage token-id oauth-token)
                oauth-token)))

          (request [oauth-token is-retry?]
            (trace "sending request" {:oauth-token oauth-token
                                      :headers     headers
                                      :body        body})
            (let [{:keys [status] :as result} (oocr/request oauth-requester oauth-token method path headers body)]
              (log/trace "remote response received" {:result result})
              (cond
                (and (= status 401) (not is-retry?)) (do (trace "remote answered with 401, will refresh and retry" {:oauth-token oauth-token})
                                                         (recur @(refresh-async oauth-token) true))
                (not (<= 200 status 299))            (throw (ex-info "request unsuccessful" {:result result}))
                true                                 result)))]

    (if-let [oauth-token (oocts/get-token token-storage token-id)]
      (-> (cond
            (has-expired? oauth-token)  (do (trace "token has expired -> refresh sync" {:oauth-token oauth-token})
                                            @(refresh-async oauth-token))
            (expires-soon? oauth-token) (do (trace "token will expire soon -> refresh async" {:oauth-token oauth-token})
                                            (refresh-async oauth-token)
                                            oauth-token))
          (or oauth-token)
          (request false))
      (throw (ex-info "no oauth-token found" {:token-id token-id})))))

(defmethod ol/start* OAuthClient [this dependencies]
  (merge this dependencies))

(defmethod ol/stop* OAuthClient [this]
  {:dependencies (select-keys this #{:oauth-requester :token-storage})
   :this         (assoc this
                        :token-storage   nil
                        :oauth-requester nil)})

(defn construct []
  (map->OAuthClient {}))
