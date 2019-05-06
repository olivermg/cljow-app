(ns ow.logging
  (:require [clojure.core.async :as a]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(def ^:dynamic +callinfo+ {:stacktrace []})

(defmacro with-logged-step [& body]
  `(binding [+callinfo+ (update +callinfo+ :stacktrace conj (rand-int 10000))]
     ~@body))

(defmacro with-logging-data [data & body]
  `(binding [+callinfo+ (update +callinfo+ :data merge ~data)]
     ~@body))

(defmacro log [& msgs]
  `(log/info (pr-str (assoc +callinfo+
                            :message (s/join " " (list ~@msgs))))))


(defn foo1 [x]
  (log "foo1 1" x)
  (Thread/sleep (rand-int 1000))
  (log "foo1 2" x)
  (inc x))

(defn foo2 [x]
  (log "foo2 1" x)
  (Thread/sleep (rand-int 1000))
  (log "foo2 2" x)
  (inc x))

(defn bar1 [x]
  (log "bar1 1" x)
  (let [r (doall (pvalues (with-logged-step
                            (foo1 (inc x)))
                          (with-logged-step
                            (foo2 (inc x)))))]
    (log "bar1 2" x)
    r) )

(defn bar2 [x]
  (log "bar2 1" x)
  (let [r (doall (pvalues (with-logged-step
                            (foo1 (inc x)))
                          (with-logged-step
                            (foo2 (inc x)))))]
    (log "bar2 2" x)
    r))

(defn baz [x]
  (log "baz 1" x)
  (with-logging-data {:user "user123"}
    (pvalues (with-logged-step
               (bar1 (inc x)))
             (with-logged-step
               (bar2 (inc x)))))
  (log "baz 2" x))
