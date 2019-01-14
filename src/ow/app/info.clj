(ns ow.app.info
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [trptcolin.versioneer.core :as version]
            [digest :refer [sha-256]]))


(defonce ^:private +version-hashes+ (atom {}))


(defn get-info []
  (let [sysinfo (->> (System/getProperties)
                     (remove (fn [[k _]] (str/includes? k "path")))
                     (map (fn [[k v]] [(keyword k) v]))
                     (into {}))]
    (assoc sysinfo :env (System/getenv))))


(defn get-project-version [group-id artifact-id]
  (version/get-version group-id artifact-id))


(defn get-project-version-hash [group-id artifact-id]
  (if-let [ex-hash (get-in @+version-hashes+ [group-id artifact-id])]
    ex-hash
    (let [version (get-project-version group-id artifact-id)
          hash (sha-256 version)]
      (log/debug "calculated project version hash" {:version version
                                                    :hash hash})
      (swap! +version-hashes+
             #(assoc-in % [group-id artifact-id] hash))
      hash)))
