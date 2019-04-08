(ns ow.comm-system
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.comm :as c]))

;;; example for definition:
#_{:component1 (fn [this request]
                 (log/debug "component1 handler received request:" request)
                 {:foo "bar1"})

   :component2 {:handler (fn [this request]
                           (log/debug "component2 handler received request:" request)
                           {:foo "bar2"})
                :config  {:port 8080}}}



(defn make-system [definition]
  )
