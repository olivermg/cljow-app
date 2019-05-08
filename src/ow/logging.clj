(ns ow.logging
  (:refer-clojure :rename {defn defn-clj
                           fn   fn-clj})
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

(defmacro fn [name [& args] & body]
  (let [argaliases (repeatedly (count args) gensym)]  ;; simplify e.g. destrucutring
    `(fn-clj ~name [~@argaliases]
             (with-trace* ~name [~@argaliases]
               (let [[~@args] [~@argaliases]]
                 ~@body)))))

(defmacro defn [name [& args] & body]
  (let [argaliases (repeatedly (count args) gensym)]  ;; simplify e.g. destructuring
    `(defn-clj ~name [~@argaliases]
       (with-trace* ~name [~@argaliases]
         (let [[~@args] [~@argaliases]]
           ~@body)))))

(defmacro with-trace-data [data & body]
  `(binding [+callinfo+ (update +callinfo+ :data merge ~data)]
     ~@body))


(defmacro log-data [name & [msg data]]
  (let [datasym (gensym (str name "-data-"))]
    `(let [~datasym ~data]
       (-> +callinfo+
           (assoc :name ~(str name)
                  :time (java.util.Date.)
                  :ns   ~(str *ns*))
           ~(if msg
              `(assoc :msg ~msg)
              `identity)
           ~(if data
              `(update :data merge (if (map? ~datasym)
                                     ~datasym
                                     {~(keyword datasym) ~datasym}))
              `identity)))))

(defmacro log-str [name & [msg data]]
  `(pr-str (log-data ~name ~msg ~data)))

(defmacro log [level name & [msg data]]
  `(log/log ~level (log-str ~name ~msg ~data)))

(defmacro trace [name & [msg data]]
  `(log :trace ~name ~msg ~data))

(defmacro debug [name & [msg data]]
  `(log :debug ~name ~msg ~data))

(defmacro info [name & [msg data]]
  `(log :info ~name ~msg ~data))

(defmacro warn [name & [msg data]]
  `(log :warn ~name ~msg ~data))

(defmacro error [name & [msg data]]
  `(log :error ~name ~msg ~data))

(defmacro fatal [name & [msg data]]
  `(log :fatal ~name ~msg ~data))



#_(do (require '[clojure.core.async :as a])

    (let [foo1r (a/chan)
          foo1a (a/chan)
          foo2r (a/chan)
          foo2a (a/chan)]

      (a/go-loop [x (a/<! foo1r)]
        (when-not (nil? x)
          (warn foo11 "foo1 1" x)
          (Thread/sleep (rand-int 1000))
          (info foo12 "foo1 2" x)
          (a/put! foo1a (inc x))))

      (a/go-loop [x (a/<! foo2r)]
        (when-not (nil? x)
          (warn foo21 "foo2 1" x)
          (Thread/sleep (rand-int 1000))
          (info foo22 "foo2 2" x)
          (a/put! foo2a (inc x))))

      (defn bar1 [x]
        (warn bar11 "bar1 1" x)
        (let [r (doall (pvalues (do (a/put! foo1r (inc x))
                                    (a/<!! foo1a))
                                (do (a/put! foo2r (inc x))
                                    (a/<!! foo2a))))]
          (info bar12 "bar1 2" x)
          r))

      (defn bar2 [x]
        (warn bar21 "bar2 1" x)
        (let [r (doall (pvalues (do (a/put! foo1r (inc x))
                                    (a/<!! foo1a))
                                (do (a/put! foo2r (inc x))
                                    (a/<!! foo2a))))]
          (info bar22 "bar2 2" x)
          r))

      (defn baz [x]
        (warn baz1 "baz 1" x)
        (with-trace-data {:user "user123"}
          (pvalues (bar1 (inc x))
                   (bar2 (inc x))))
        (info baz2 "baz 2" x))

      (baz 123)
      (Thread/sleep 3000)
      (a/close! foo2a)
      (a/close! foo2r)
      (a/close! foo1a)
      (a/close! foo1r)))
