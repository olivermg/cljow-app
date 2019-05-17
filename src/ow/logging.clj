(ns ow.logging
  (:refer-clojure :rename {defn defn-clj
                           fn   fn-clj
                           let  let-clj})
  (:require [clojure.core.async :as a]
            [clojure.string :as s]
            [clojure.tools.logging :as log])
  (:import [clojure.lang IObj]))

(def ^:dynamic +callinfo+ {:trace []})

(defn-clj pr-str-map-vals [m]
  (->> m
       (map (fn-clj [[k v]]
              [k (pr-str v)]))
       (into {})))

(defn-clj merge-loginfo [loginfo1 loginfo2]
  (update loginfo2 :trace #(-> (concat (:trace loginfo1) %)
                               vec)))

(defn-clj merge-loginfos [& loginfos]
  (reduce merge-loginfo loginfos))

(defmacro make-trace-info* [name & args]  ;; TODO: create record for trace step, to prevent overly verbose printing (e.g. of large arguments)
  `(into {:id   (rand-int Integer/MAX_VALUE)
          :time (java.util.Date.)
          :fn   ~(str *ns* "/" name)}
         [~(when-not (empty? args)
             `[:args (map pr-str (list ~@args))])]))

(defmacro with-trace* [name [& args] & body]
  `(binding [+callinfo+ (update +callinfo+ :trace conj (make-trace-info* ~name ~@args))]
     ~@body))

(defmacro with-trace
  "Adds an entry into the current trace history."
  [name & body]
  `(with-trace* ~name []
     ~@body))

(defn-clj lhs-aliases [args]
  (letfn [(map-name [m]
            (str (or (reduce (fn-clj [name [k v]]
                                     (if (= k :as)
                                       v
                                       name))
                             nil m)
                     "destructed-map")
                 "-"))

          (vec-name [v]
            (str (or (some-> (reduce (fn-clj [[capture? name] elem]
                                             (cond
                                               capture?     [false elem]
                                               (= elem :as) [true  name]
                                               true         [false name]))
                                     [false nil] v)
                             second)
                     "destructred-vector")
                 "-"))

          (alias-arg [arg]
            (cond
              (map? arg)    (gensym (map-name arg))
              (vector? arg) (gensym (vec-name arg))
              (symbol? arg) (if (not= arg '&)
                              (gensym (str arg "-"))
                              arg)
              true          (throw (ex-info "don't know how to create alias for arg" {:arg arg}))))]

    (map alias-arg args)))

(defn-clj remove-& [lhs-aliases]
  (remove #(= % '&) lhs-aliases))

(defmacro fn
  "Same as clojure.core/fn, but also adds an entry into the current trace history upon invocation of the fn."
  [name [& args] & body]
  (let-clj [lhs (lhs-aliases args)
            rhs (remove-& lhs)]  ;; simplify e.g. destructuring
    `(fn-clj ~name [~@lhs]
             (with-trace* ~name [~@rhs]
               (let-clj [[~@(remove-& args)] [~@rhs]]
                 ~@body)))))

(defmacro defn
  "Same as clojure.core/defn, but also adds en entry into the current trace history upon invocation of the defn."
  [name [& args] & body]
  (let-clj [lhs (lhs-aliases args)
            rhs (remove-& lhs)]  ;; simplify e.g. destructuring
    `(defn-clj ~name [~@lhs]
       (with-trace* ~name [~@rhs]
         (let-clj [[~@(remove-& args)] [~@rhs]]
           ~@body)))))

(defmacro with-trace-data
  "Adds user data into the current trace info map that will be available in subsequent log invocations."
  [data & body]
  `(binding [+callinfo+ (update +callinfo+ :data merge (pr-str-map-vals ~data))]
     ~@body))


(defn-clj get-trace-root
  "Returns the root/first/topmost entry in the current trace history."
  []
  (get-in +callinfo+ [:trace 0]))


(defmacro log-data
  "Returns the current trace info map plus some augmented data (e.g. timestamp)."
  [name & [msg data]]
  (let-clj [datasym (gensym (str name "-data-"))]
    `(let-clj [~datasym ~data]
       (-> +callinfo+
           (assoc :name ~(str name)
                  :time (java.util.Date.)
                  :ns   ~(str *ns*))
           ~(if msg
              `(assoc :msg (str ~msg))
              `identity)
           ~(if data
              `(update :data merge (if (map? ~datasym)
                                     (pr-str-map-vals ~datasym)
                                     {~(keyword datasym) (pr-str ~datasym)}))
              `identity)))))

(defmacro log-str
  "Returns the current trace info map formatted as string."
  [name & [msg data]]
  `(pr-str (log-data ~name ~msg ~data)))

(defmacro log
  "Prints a log message based on the current trace info map."
  [level name & [msg data]]
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


(defn-clj attach
  "Attaches the current trace info map as metadata to data. Will not do anything if data is
   not an instance of IObj, because metadata cannot be attached in this case."
  [data]
  (if (instance? IObj data)
    (with-meta data
      {::callinfo +callinfo+})
    (do (info attach "cannot attach log information to data not implementing IObj" {:data data})
        data)))

(defn-clj detach
  "Returns the attached trace info map from data."
  [data]
  (some-> data meta ::callinfo))

(defmacro with-loginfo
  "Sets the current trace info map to loginfo."
  [loginfo & body]
  `(binding [+callinfo+ (merge-loginfo ~loginfo +callinfo+)]
     ~@body))

(defmacro let
  "Like clojure.core/let, but also sets the current trace info map to
  potential trace info maps that might be attached to the given values."
  [[& bindings] & body]
  (let-clj [bindings (partition 2 bindings)
            rhs      (->> bindings (map first) lhs-aliases remove-&)]
    `(let-clj [~@(mapcat (fn-clj [[sym value] alias]
                                 `[~alias ~value])
                         bindings rhs)
               ~@(mapcat (fn-clj [[sym value] alias]
                                 `[~sym ~alias])
                         bindings rhs)]
       (binding [+callinfo+ (apply merge-loginfos (list ~@rhs +callinfo+))]
         ~@body))))



#_(let-clj [foo1r (a/chan)
          foo1a (a/chan)
          foo2r (a/chan)
          foo2a (a/chan)]

  (a/go-loop [x (a/<! foo1r)]
    (when-not (nil? x)
      (let [[x] x]
        (with-trace inside-foo1
          (warn foo11 "foo1-1" x)
          (Thread/sleep (rand-int 1000))
          (info foo12 "foo1-2" x)
          (a/put! foo1a (->> x inc vector attach))))
      (recur (a/<! foo1r))))

  (a/go-loop [x (a/<! foo2r)]
    (when-not (nil? x)
      (let [x x]
        (warn foo21 "foo2-1" x)
        (Thread/sleep (rand-int 1000))
        (info foo22 "foo2-2" x)
        (a/put! foo2a (->> x inc attach)))
      (recur (a/<! foo2r))))

  (defn bar1 [x]
    (warn bar11 "bar1-1" x)
    (let-clj [r (doall (pvalues (do (a/put! foo1r (->> x inc vector attach))
                                    (let [[res] (a/<!! foo1a)]
                                      res))
                                (do (a/put! foo2r (->> x inc attach))
                                    (let [res (a/<!! foo2a)]
                                      res))))]
      (info bar12 "bar1-2" x)
      r))

  (defn bar2 [x {:keys [d1] :as mmm} [d2 :as vvv] & {:keys [xyz]}]
    (warn bar21 "bar2-1" x)
    (let-clj [r (doall (pvalues (do (a/put! foo1r (->> x inc vector attach))
                                    (let [[res] (a/<!! foo1a)]
                                      res))
                                (do (a/put! foo2r (->> x inc attach))
                                    (let [res (a/<!! foo2a)]
                                      res))))]
      (info bar22 "bar2-2" x)
      r))

  (defn baz [x]
    (warn baz1 "baz-1" x)
    (with-trace-data {:user "user123"}
      (pvalues (bar1 (inc x))
               (bar2 (inc x) nil nil)))
    (info baz2 "baz-2" x))

  (baz 123)
  (Thread/sleep 3000)
  (a/close! foo2a)
  (a/close! foo2r)
  (a/close! foo1a)
  (a/close! foo1r))

#_(defn foo [a b {:keys [c d] :as m} [e f :as v] & [g h]]
  [a b c d e f g h])
