(ns ow.logging
  (:require [clojure.core.async :as a]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(def ^:dynamic +callinfo+ {:trace []})

#_(defmacro with-logged-step [& body]
  `(binding [+callinfo+ (update +callinfo+ :trace conj {:id   (rand-int 10000)
                                                        ;;;:form ~(str &form)
                                                        :ns   ~(str *ns*)
                                                        :time (java.util.Date.)
                                                        #_:fn   #_(-> (Thread/currentThread)
                                                                  .getStackTrace
                                                                  (nth 2)
                                                                  str)})]
     ~@body))

(defmacro defnl [name [& args] & body]
  `(defn ~name [~@args]
     (binding [+callinfo+ (update +callinfo+ :trace conj {:id   (rand-int 10000)
                                                          ;;;:ns   ~(str *ns*)
                                                          :time (java.util.Date.)
                                                          :fn   ~(str *ns* "/" name)
                                                          :args (list ~@args)})]
       ~@body)))

(defmacro with-logging-data [data & body]
  `(binding [+callinfo+ (update +callinfo+ :data merge ~data)]
     ~@body))

(defmacro log [& msgs]
  `(log/info (pr-str (assoc +callinfo+
                            :msg  (s/join " " (list ~@msgs))
                            ;;;:form ~(str &form)
                            :time (java.util.Date.)
                            :ns   ~(str *ns*)))))


#_(do (defnl foo1 [x]
      (log "foo1 1" x)
      (Thread/sleep (rand-int 1000))
      (log "foo1 2" x)
      (inc x))

    (defnl foo2 [x]
      (log "foo2 1" x)
      (Thread/sleep (rand-int 1000))
      (log "foo2 2" x)
      (inc x))

    (defnl bar1 [x]
      (log "bar1 1" x)
      (let [r (doall (pvalues #_(with-logged-step)
                              (foo1 (inc x))
                              #_(with-logged-step)
                              (foo2 (inc x))))]
        (log "bar1 2" x)
        r) )

    (defnl bar2 [x]
      (log "bar2 1" x)
      (let [r (doall (pvalues #_(with-logged-step)
                              (foo1 (inc x))
                              #_(with-logged-step)
                              (foo2 (inc x))))]
        (log "bar2 2" x)
        r))

    (defnl baz [x]
      (log "baz 1" x)
      (with-logging-data {:user "user123"}
        (pvalues #_(with-logged-step)
                 (bar1 (inc x))
                 (do (Thread/sleep 2000)
                     #_(with-logged-step)
                     (bar2 (inc x)))))
      (log "baz 2" x))

    #_(with-logged-step)
    #_(baz 123))
