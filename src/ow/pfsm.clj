(ns ow.pfsm)

(defn advance [{:keys [state def inputs] :as pfsm} input]
  (let [def-inputs (get def state)
        def-inputs (if-not (coll? def-inputs)
                     #{def-inputs}
                     def-inputs)
        inputs     (conj inputs input)]
    (if (= inputs def-inputs)
      (-> pfsm
          (update :state inc)
          (assoc :inputs    #{}
                 :advanced? true))
      (assoc pfsm
             :inputs    inputs
             :advanced? false))))

(defn make-pfsm [def]
  {:state     0
   :inputs    #{}
   :def       def
   :advanced? false})



#_[:a #{:b :c} :d]

(let [pfsm-def [:a #{:b :c} :d]]
  (letfn [(xf [rf]
            (fn
              ([] (rf))
              ([pfsms] (rf pfsms))
              ([pfsms {:keys [id signal] :as input}]
               (println "SIGNAL" id signal)
               (let [pfsm  (or (get pfsms id)
                               (make-pfsm pfsm-def))
                     {:keys [advanced? state def] :as pfsm} (advance pfsm signal)
                     pfsms (assoc pfsms id pfsm)]
                 (when advanced?
                   (let [ss (get def state)]
                     (doseq [s (if (coll? ss)
                                 ss
                                 #{ss})]
                       (println "INVOKE" id s))))
                 (rf pfsms input)))))]
    (transduce xf (fn [& [pfsms input]]
                    (println "  " pfsms input)
                    pfsms)
               {}
               [#_{:id 11 :signal :a}
                {:id 22 :signal :a}
                #_{:id 11 :signal :b}
                {:id 22 :signal :c}
                {:id 22 :signal :b}
                #_{:id 11 :signal :c}])))
