(ns ow.oauth.client
  (:require [clojure.tools.logging :as log]
            [ow.clojure :as owclj]
            [ow.lifecycle :as ol]
            [ow.oauth.client.requester :as oocr]
            [ow.oauth.client.token-storage :as oocts]))

(defrecord OAuthClient [token-storage oauth-requester])

(defn grant [{:keys [token-storage oauth-requester] :as this} token-id code]
  (let [oauth-token (oocr/grant-via-authorization-code oauth-requester code)]
    (oocts/set-token token-storage token-id oauth-token)
    oauth-token))

(defn get-token [{:keys [token-storage] :as this} token-id]
  (oocts/get-token token-storage token-id))  ;; TODO: don't return it when it's expired

(defn request [{:keys [token-storage oauth-requester] :as this} token-id method path & {:keys [headers body]}]
  (letfn [(has-expired? [expires-at]
            false)

          (expires-soon? [expires-at]
            false)

          (refresh [oauth-token]  ;; FIXME: implement protection for multiple overlapping refreshes
            #_(owclj/tries [5
                          :try-sym       try
                          :exception-f   (fn [e try]
                                           (log/warn "refresh failed" try (str e)))
                          :retry-delay-f (owclj/make-retry-delay-log10-f :exp 1.5 :cap (* 60 1000) :varpct 10.0)]
                         (log/trace "refresh, try" try))
            (let [oauth-token (oocr/refresh oauth-requester oauth-token)]
              (oocts/set-token token-storage token-id oauth-token)
              oauth-token))

          (refresh-async [oauth-token]
            (future
              (refresh oauth-token)))

          (request [oauth-token]
            (let [{:keys [status] :as result} (oocr/request oauth-requester oauth-token method path headers body)]
              (if-not (= status 401)
                result
                (let [oauth-token (refresh oauth-token)]
                  (oocr/request oauth-requester oauth-token method path headers body)))))]

    (if-let [{:keys [expires-at] :as oauth-token} (oocts/get-token token-storage token-id)]
      (request (or (cond
                     (has-expired? expires-at)  (refresh oauth-token)
                     (expires-soon? expires-at) (do (refresh-async oauth-token) nil))
                   oauth-token))
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
