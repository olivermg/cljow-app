(ns ow.clojure
  (:import [clojure.lang IDeref]))

(defn try-times [n f & {:keys [interleave-f]}]
  (loop [curn 1]
    (let [{:keys [::result ::exception]} (try
                                           {::result (f curn)}
                                           (catch Exception e
                                             (if (< curn n)
                                               {::exception e}
                                               (throw e)))
                                           (catch Error e
                                             (if (< curn n)
                                               {::exception e}
                                               (throw e))))]
      (if-not exception
        result
        (do (when interleave-f
              (let [inter-res (interleave-f exception curn)]
                (if (instance? IDeref inter-res)
                  @inter-res
                  inter-res)))
            (recur (inc curn)))))))

