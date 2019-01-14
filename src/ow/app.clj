(ns ow.app
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ow.app.config :as cfg]
            [ow.app.info :as info]))

(defn run [profile]
  (log/info "SYSTEM INFO" (info/get-info))
  (let [config (cfg/config (io/resource "config.edn") profile)]
    (ig/init config)))

(defn quit [system]
  (and system (ig/halt! system))
  nil)
