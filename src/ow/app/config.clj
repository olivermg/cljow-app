(ns ow.app.config
  (:require [aero.core :as aero]
            [integrant.core :as ig]))


(defmethod aero/reader 'ig/ref [opts tag value]
  (ig/ref value))


(defn config [filename-or-resource profile]
  (let [config (aero/read-config filename-or-resource
                                 {:profile profile
                                  ;;;:resolver {".env.edn" ".env.edn"}
                                  })]
    (ig/load-namespaces config)
    config))
