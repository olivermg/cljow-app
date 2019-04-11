(ns ow.system
  (:require [clojure.tools.logging :as log]
            [ow.system.dependencies :as sd]
            #_[ow.system.lifecycles :as sl]
            [ow.system.request-listener :as srl]))

(defn init-system [components]
  (letfn [(update-components [system f]
            (update system :components
                    #(->> (map (fn [[name component]]
                                 [name (f name component)])
                               %)
                          (into {}))))

          (init-system-component-names [system]
            (update-components system (fn [name component]
                                        (assoc component :name name))))]

    (let [init-system-fn     (comp srl/init-system
                                   sd/init-system
                                   init-system-component-names)
          init-components-fn (fn [system]
                               (let [init-fn (fn [name component]
                                               ((comp #_sl/init-component
                                                      srl/init-component)
                                                component))]
                                 (update-components system init-fn)))]

      (->> {:components components}
           init-system-fn
           init-components-fn))))

(letfn [(start-or-stop-component [{:keys [lifecycles] :as component} op-kw]
          (let [started? (= op-kw :start)]
            (reduce (fn [component lifecycle]
                      (let [f (get lifecycle op-kw identity)]
                        (-> (f component)
                            (assoc ::started? started?))))
                    component
                    lifecycles)))

        (inject-dependencies [system component]
          (update component :dependencies
                  #(->> (map (fn [depcn]
                               [depcn (get-in system [:components depcn])])
                             %)
                        (into {}))))

        (deject-dependencies [_ component]
          (update component :dependencies
                  #(-> (map (fn [[depcn _]]
                              depcn)
                            %)
                       set)))

        (lookup-component-xf [xf]
          (fn
            ([]
             (xf))
            ([system]
             (xf system))
            ([system component-name]
             (xf system (get-in system [:components component-name])))))

        (make-inject-or-deject-dependencies-xf [op-kw]
          (let [op (case op-kw
                     :inject inject-dependencies
                     :deject deject-dependencies)]
            (fn [xf]
              (fn
                ([]
                 (xf))
                ([system]
                 (xf system))
                ([system {:keys [name] :as component}]
                 (let [resulting-component (op system component)]
                   (xf (assoc-in system [:components name] resulting-component) resulting-component)))))))

        (make-start-or-stop-xf [op-kw]
          (fn [xf]
            (fn
              ([]
               (xf))
              ([system]
               (xf system))
              ([system {:keys [name] :as component}]
               (let [started-component (start-or-stop-component component op-kw)]
                 (xf (assoc-in system [:components name] started-component) started-component))))))]

  (defn start-system [{:keys [start-order] :as system}]
    (transduce (comp lookup-component-xf
                     (make-inject-or-deject-dependencies-xf :inject)
                     (make-start-or-stop-xf :start))
               (fn [& [system component]]
                 system)
               system start-order))

  (defn stop-system [{:keys [start-order] :as system}]
    (transduce (comp lookup-component-xf
                     (make-start-or-stop-xf :stop)
                     (make-inject-or-deject-dependencies-xf :deject))
               (fn [& [system component]]
                 system)
               system (reverse start-order))))



#_(let [components {:c1 {:lifecycles [{:start (fn [this]
                                              (println "START C1")
                                              this)
                                     :stop   (fn [this]
                                               (println "STOP C1")
                                               this)}]
                       :config {:foo (System/getenv "FOO")}}

                  :c2 {:lifecycles [{:start (fn [this]
                                              (println "START C2")
                                              (println "  C2 DEPENDS ON C1:" )
                                              this)
                                     :stop (fn [this]
                                             (println "STOP C2")
                                             this)}]
                       :config {:bar (System/getenv "BAR")}
                       :dependencies #{:c1}}

                  :c3 {:request-listener {:topic :foo1
                                          :handler (fn [this request]
                                                     (println "RECEIVED REQUEST C3" request))}
                       :lifecycles [{:start (fn [this]
                                              (println "START C3")
                                              this)
                                     :stop (fn [this]
                                             (println "STOP C3")
                                             this)}]
                       :dependencies #{:c4}}

                  :c4 {:request-listener {:topic :foo2
                                          :handler (fn [this request]
                                                     (println "RECEIVED REQUEST C4" request))}
                       :lifecycles [{:start (fn [this]
                                              (println "START C4")
                                              this)
                                     :stop (fn [this]
                                             (println "STOP C4")
                                             this)}]}

                  :c5 {:lifecycles [{:start (fn [this]
                                              (println "START C5")
                                              this)
                                     :stop (fn [this]
                                             (println "STOP C5")
                                             this)}]}}]

  (-> components
      init-system
      start-system
      #_stop-system
      clojure.pprint/pprint))
