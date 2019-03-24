(ns ow.comm.events.emitter
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [ch]
  (owl/construct :emitter {:ch ch}))

(defmethod owl/start :emitter [this]
  (println "emitter start")
  this)

(defmethod owl/stop :emitter [this]
  (println "emitter stop")
  this)

(defn emit [{:keys [ch] :as this} data]
  (a/put! ch data))
