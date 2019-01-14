(ns ow.app.config
  (:require [integrant.core :as ig]
            [aero.core :as aero]))


(defmethod aero/reader 'ig/ref [opts tag value]
  (ig/ref value))


(defn config [filename-or-resource profile]
  (let [config (aero/read-config filename-or-resource
                                 {:profile profile
                                  ;;;:resolver {".env.edn" ".env.edn"}
                                  })]
    (ig/load-namespaces config)
    config))
