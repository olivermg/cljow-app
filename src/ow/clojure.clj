(ns ow.clojure
  (:require [clojure.core.async :as a])
  (:import [clojure.lang IDeref]))

(defn make-retry-delay-log10-f
  "Returns function that calculates a number of milliseconds based on n-th try.
  The resulting function expects to be called with a 1-based number of tries (i.e.
  the first try is 1, not 0).

  exp is the exponential by which to scale logarithmic value.
  cap is a maximum cap that should not be exceeded.
  varpct is the percentage by which to randomize the result."
  [& {:keys [exp cap varpct]}]
  (let [exp    (double (or exp 1.0))
        cap    (double (or cap Long/MAX_VALUE))
        varpct (double (or varpct 10.0))]
    (fn [n]
      (let [n       (inc n)
            msec    (* (Math/log10 n)
                       (Math/pow n exp)
                       1000.0)
            msec    (min msec cap)
            varcap  (* (/ msec 100.0)
                       varpct)
            varmsec (- (rand-int varcap)
                       (/ varcap 2.0))]
        (long (+ msec varmsec))))))

(defmacro tries [[n & {:keys [try-sym exception-f retry-delay-f]}] & body]
  (let [e            (gensym "e-")
        try-sym      (or try-sym (gensym "try-"))
        result       (gensym "result-")
        exception    (gensym "exception-")
        delay        (gensym "delay-")
        p            (gensym "promise-")
        [try-body try-ops] (reduce (fn [[try-body try-ops] expr]
                                     (if-not (and (list? expr)
                                                  (or (= (first expr) 'catch)
                                                      (= (first expr) 'finally)))
                                       [(conj try-body expr) try-ops]
                                       [try-body             (conj try-ops expr)]))
                                   ['() '()]
                                   (reverse body))]
    `(try
       (loop [~try-sym 1]
         (let [{~result :result ~exception :exception}
               (try
                 {:result (do ~@try-body)}
                 (catch Throwable ~e
                   {:exception ~e}))]
           (if-not ~exception
             ~result
             (if (< ~try-sym ~n)
               (do ~(when exception-f
                      `(~exception-f ~exception ~try-sym))
                   ~(when retry-delay-f
                      `(let [~delay (~retry-delay-f ~try-sym)
                             ~p     (promise)]
                         (a/go
                           (a/<! (a/timeout ~delay))
                           (deliver ~p true))
                         (deref ~p)))
                   (recur (inc ~try-sym)))
               (throw ~exception)))))
       ~@try-ops)))



#_(oc/tries
 [5
  :interleave-f (fn [exception try]
                  (println "TRY" try (str exception))
                  (Thread/sleep 500))
  :try-sym try]
 (println 123)
 (when (<= try 20)
   (throw (ex-info "foobar" {})))
 (println "xyz")
 :xxx
 (catch Exception ex
   (println "EXCEPTION" ex)
   ex)
 (catch Error e
   (println "ERROR" e)
   e)
 (finally
   (println "FINALLY")))
