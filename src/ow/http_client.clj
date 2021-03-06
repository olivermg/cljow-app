(ns ow.http-client
  (:require #_[clojure.tools.logging :as log]
            [ow.logging.api.alpha :as log]
            [org.httpkit.client :as http])
  (:import [java.net URI]
           [javax.net.ssl SSLEngine SSLParameters SNIHostName]))

(defn make-client []
  (letfn [(sni-configure [^SSLEngine ssl-engine ^URI uri]
            (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
              (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
              (.setSSLParameters ssl-engine ssl-params)
              (.setUseClientMode ssl-engine true)))]
    (http/make-client {:ssl-configurer sni-configure})))
