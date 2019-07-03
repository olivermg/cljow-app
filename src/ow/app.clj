(ns ow.app
  (:require #_[clojure.java.io :as io]
            #_[clojure.tools.logging :as log]
            [ow.logging.api.alpha :as log]
            #_[integrant.core :as ig]
            #_[ow.app.config :as cfg]
            [ow.app.info :as info]
            [ow.system :as ows]))

(defn run [components]
  (log/info "SYSTEM INFO" (info/get-info))
  (-> components
      (ows/init-system)
      (ows/start-system)))

(defn quit [system]
  (ows/stop-system system))
