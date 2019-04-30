(ns ow.oauth.client.token-storage
  (:refer-clojure :rename {update update-clj})
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(defonce ^:private +refresh-promises+ (atom {}))

(defprotocol TokenStorage
  (get-token [this id])
  (set-token [this id token-data]))

(defn update [this id token-data]
  )

(defn get-refresh-promise [this id]
  (-> (swap! +refresh-promises+ update id #(if (or (nil? %)
                                                   (t/after? (t/now) (:expires-at %)))
                                             {:promise    (promise)
                                              :expires-at (t/plus (t/now) (t/seconds 20))}
                                             %))
      :promise))
