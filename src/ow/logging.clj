(ns ow.logging
  (:require [clojure.core.async :as a]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(def ^:dynamic +callinfo+ {:trace []})

(defmacro make-trace-info* [name & args]
  `(into {:id   (rand-int 10000)
          :time (java.util.Date.)
          :fn   ~(str *ns* "/" name)}
         [~(when-not (empty? args)
             `[:args (list ~@args)])]))

(defmacro with-trace* [name [& args] & body]
  `(binding [+callinfo+ (update +callinfo+ :trace conj (make-trace-info* ~name ~@args))]
     ~@body))

(defmacro with-trace [name & body]
  `(with-trace* ~name []
     ~@body))

(defmacro defn-traced [name [& args] & body]
  `(defn ~name [~@args]
     (with-trace* ~name [~@args]
       ~@body)))

(defmacro with-trace-data [data & body]
  `(binding [+callinfo+ (update +callinfo+ :data merge ~data)]
     ~@body))

(defmacro log [& msgs]
  `(log/info (pr-str (assoc +callinfo+
                            :msg  (s/join " " (list ~@msgs))
                            ;;;:form ~(str &form)
                            :time (java.util.Date.)
                            :ns   ~(str *ns*)))))


#_(do (defn-traced foo1 [x]
      (log "foo1 1" x)
      (Thread/sleep (rand-int 1000))
      (log "foo1 2" x)
      (inc x))

    (defn-traced foo2 [x]
      (log "foo2 1" x)
      (Thread/sleep (rand-int 1000))
      (log "foo2 2" x)
      (inc x))

    (defn-traced bar1 [x]
      (log "bar1 1" x)
      (let [r (doall (pvalues #_(with-logged-step)
                              (foo1 (inc x))
                              #_(with-logged-step)
                              (foo2 (inc x))))]
        (log "bar1 2" x)
        r) )

    (defn-traced bar2 [x]
      (log "bar2 1" x)
      (let [r (doall (pvalues #_(with-logged-step)
                              (foo1 (inc x))
                              #_(with-logged-step)
                              (foo2 (inc x))))]
        (log "bar2 2" x)
        r))

    (defn-traced baz [x]
      (log "baz 1" x)
      (with-trace-data {:user "user123"}
        (pvalues #_(with-logged-step)
                 (bar1 (inc x))
                 (do (Thread/sleep 2000)
                     #_(with-logged-step)
                     (bar2 (inc x)))))
      (log "baz 2" x))

    #_(with-logged-step)
    (baz 123))
