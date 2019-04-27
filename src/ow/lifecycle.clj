(ns ow.lifecycle
  (:require [clojure.tools.logging :as log]))

(defn construct [& args])
(defn get-name [& args])
(defn get-type [& args])



(defmulti start* (fn [this dependencies] (type this)))
(defmulti stop* (fn [this] (type this)))

(defmethod start* :default [this dependencies] (merge this dependencies))
(defmethod stop* :default [this] this)

(defn start
  ([this dependencies]
   (log/info (str "Starting " (type this)))
   (start* this dependencies))
  ([this]
   (start this {})))

(defn stop [this]
  (log/info (str "Stopping " (type this)))
  (stop* this))
