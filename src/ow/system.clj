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

(letfn [(start-or-stop-component [{:keys [lifecycles] :as component} fn-kw]
          (reduce (fn [component lifecycle]
                    (let [f (get lifecycle fn-kw identity)]
                      (f component)))
                  component
                  lifecycles))

        (start-or-stop-components [system ordered-component-names fn-kw]
          (reduce (fn [system component-name]
                    (update-in system [:components component-name] start-or-stop-component fn-kw))
                  system
                  ordered-component-names))]

  (defn start-system [{:keys [start-order] :as system}]
    (start-or-stop-components system start-order :start))

  (defn stop-system [{:keys [start-order] :as system}]
    (start-or-stop-components system (reverse start-order) :stop)))



(let [components {:c1 {:lifecycles [{:start (fn [this]
                                              (println "START C1")
                                              this)
                                     :stop   (fn [this]
                                               (println "STOP C1")
                                               this)}]
                       :config {:foo (System/getenv "FOO")}}

                  :c2 {:lifecycles [{:start (fn [this]
                                              (println "START C2")
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
      stop-system
      clojure.pprint/pprint))
