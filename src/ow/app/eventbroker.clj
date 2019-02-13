(ns ow.app.eventbroker
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defn start [{{:keys [in-mult in-chan dispatch-map]} ::eventbroker-config :as this}]
  (if-not in-chan
    (let [in-chan (a/chan)
          in-pub (-> in-mult (a/tap in-chan) (a/pub ::type))]
      (doall
       (map (fn [[evtype handler]]
              (let [handle (fn [data {:keys [::response-chan ::error-chan] :as receipt}]
                             (letfn [(handle-ex [e]
                                       (log/info (format "%s in %s/%s: %s; data: %s"
                                                         (type e) (type this) (str evtype)
                                                         (.getMessage e) (pr-str data)))
                                       (a/put! error-chan {::error e}))]
                               (try
                                 (let [result (handler this data)]
                                   (a/put! response-chan {::result result}))
                                 (catch Exception e
                                   (handle-ex e))
                                 (catch Error e
                                   (handle-ex e)))))
                    sub-chan (a/chan)]
                (a/sub in-pub evtype sub-chan)
                (println "START" evtype)
                (a/go-loop [{:keys [::data ::receipt] :as msg} (a/<! sub-chan)]
                  (if-not (nil? data)
                    (do (handle data receipt)
                        (recur (a/<! sub-chan)))
                    (println "STOP" evtype)))))
            dispatch-map))
      (assoc-in this [::eventbroker-config :in-chan] in-chan))
    this))

(defn stop [{{:keys [in-mult in-chan dispatch-map]} ::eventbroker-config :as this}]
  (if in-chan
    (do (a/untap in-mult in-chan)
        (a/close! in-chan)
        (assoc-in this [::eventbroker-config :in-chan] nil))
    this))

(defn eventify [component in-mult out-chan dispatch-map]
  (let [eventbroker-config {:in-mult in-mult
                            :out-chan out-chan
                            :dispatch-map dispatch-map}]
    (assoc component ::eventbroker-config eventbroker-config)))

(defn emit [{{:keys [out-chan]} ::eventbroker-config :as this} evtype data]
  (let [receipt {::error-chan (a/promise-chan)
                 ::response-chan (a/promise-chan)}
        event {::receipt receipt
               ::data data
               ::type evtype}]
    (a/put! out-chan event)
    receipt))

(defn wait [this {:keys [::response-chan ::error-chan] :as receipt}]
  (let [[msg ch] (a/alts!! [response-chan error-chan])]
    (a/close! response-chan)
    (a/close! error-chan)
    (condp = ch
      response-chan (::result msg)
      error-chan    (let [e (::error msg)]
                      (throw (if (instance? Throwable e)
                               e
                               (ex-info "ERROR" {:error (::error e)})))))))



;;;
;;; TEST
;;;

(do

  (defrecord FooComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn foo-component [in-mult out-chan dep1]
    (let [dm {:foo (fn [this data]
                     (println "FOO" data)
                     (let [data    (update data :x inc)
                           receipt (emit this :bar data)
                           _       (emit this :mox data)
                           result  (wait this receipt)]
                       (println "FOO RESULT" result)
                       result))}]
      (-> (map->FooComponent {})
          (eventify in-mult out-chan dm))))

  (defn run-foo1 [this x]
    (->> (emit this :foo {:x x})
         (wait this)
         (println "FINAL RESULT")))


  (defrecord BarComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn bar-component [in-mult out-chan]
    (let [dm {:bar (fn [this data]
                     (println "BAR" data)
                     (let [data    (update data :x #(* % 2))
                           receipt (emit this :baz data)
                           result  (wait this receipt)]
                       (println "BAR RESULT" result)
                       result))}]
      (-> (map->BarComponent {})
          (eventify in-mult out-chan dm))))


  (defrecord BazComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn baz-component [in-mult out-chan]
    (let [dm {:baz (fn [this data]
                     (println "BAZ" data)
                     #_(throw (ex-info "AAAAAAARGH" {}))
                     (let [result (update data :x #(/ % 3.0))]
                       (println "BAZ RESULT" result)
                       result))}]
      (-> (map->BazComponent {})
          (eventify in-mult out-chan dm))))


  (defrecord MoxComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn mox-component [in-mult out-chan]
    (let [dm {:mox (fn [this data]
                     (println "MOX" data)
                     #_(throw (ex-info "MOOOOOOOOOOOOOOORGH" {}))
                     (let [result (assoc data :y ::y)]
                       (println "MOX RESULT" result)
                       result))}]
      (-> (map->MoxComponent {})
          (eventify in-mult out-chan dm))))


  (let [in-chan  (a/chan)
        in-mult  (a/mult in-chan)
        out-chan (a/chan)
        _        (a/pipe out-chan in-chan)
        fooc     (-> (foo-component in-mult out-chan :superdep1)
                     owl/start)
        barc     (-> (bar-component in-mult out-chan)
                     owl/start)
        bazc     (-> (baz-component in-mult out-chan)
                     owl/start)
        moxc     (-> (mox-component in-mult out-chan)
                     owl/start)]
    #_(doall
     (pvalues))
    (run-foo1 fooc 1)
    (run-foo1 fooc 2)
    (run-foo1 fooc 3)
    (Thread/sleep 2000)
    (doseq [c [moxc bazc barc fooc]]
      (Thread/sleep 50)
      (owl/stop c))))
