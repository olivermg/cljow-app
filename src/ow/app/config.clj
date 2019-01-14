(ns ow.app.config
  (:require [integrant.core :as ig]
            [aero.core :as aero]
            [clojure.java.io :as io]))

(defmethod aero/reader 'ig/ref [opts tag value]
  (ig/ref value))

(defn config [profile]
  (let [config (-> (io/resource "config.edn")
                   (aero/read-config {:profile profile
                                      ;;;:resolver {".env.edn" ".env.edn"}
                                      }))]
    (ig/load-namespaces config)
    config))
