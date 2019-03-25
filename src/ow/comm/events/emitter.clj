(ns ow.comm.events.emitter
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [ch]
  (owl/construct ::emitter {:ch ch}))

(defn emit [{:keys [ch] :as this} data]
  (a/put! ch data))
