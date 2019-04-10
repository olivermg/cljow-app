(ns ow.system
  (:require [clojure.tools.logging :as log]
            [ow.system.dependencies :as sd]
            [ow.system.lifecycles :as sl]
            [ow.system.request-listener :as srl]))

;;; example for components:
#_{:component1 {:request-listener {:topic :foo1
                                 :handler (fn [this request]
                                            (log/debug "component1 handler received request:" request)
                                            {:foo "bar1"})}}

 :component2 {:request-listener {:topic :foo2
                                 :handler (fn [this request]
                                            (log/debug "component2 handler received request:" request)
                                            {:foo "bar2"})}
              :config       {:port 8080}
              :dependencies #{:component3}}

 :component3 {:request-listener {:topic :foo3
                                 :handler (fn [this request]
                                            (log/debug "component3 handler received request:" request)
                                            {:foo "bar3"})}
              :lifecycles [{:start     (fn [this]
                                         (assoc this :moo 123))
                            :stop      (fn [this]
                                         (assoc this :moo nil))}]
              :config {:port 1234}}}

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



#_(let [components {:c1 {:lifecycles [{:start (fn [this]
                                              (println "START C1")
                                              this)
                                     :stop   (fn [this]
                                               (println "STOP C1")
                                               this)}]}
                  :c2 {:dependencies #{:c1}}
                  :c3 {:request-listener {:topic :foo1
                                          :handler (fn [this request]
                                                     (println "RECEIVED REQUEST" request))}}}]
  (-> (init-system components)))
