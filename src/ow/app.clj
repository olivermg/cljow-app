(ns ow.app
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ow.app.config :as cfg]
            [ow.app.info :as info]))

(defn start [profile]
  (log/info "SYSTEM INFO" (info/get-info))
  (let [config (cfg/config profile)]
    (ig/init config)))

(defn stop [system]
  (and system (ig/halt! system))
  nil)
