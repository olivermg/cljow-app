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

(defn emit
  ([{{:keys [out-chan]} ::eventbroker-config :as this} receipt evtype data]
   (let [error-chan (a/promise-chan)
         receipt (-> (or receipt
                         {::error-chan error-chan
                          ::error-mult (a/mult error-chan)})
                     (assoc ::response-chan (a/promise-chan)))
         event {::receipt receipt
                ::data data
                ::type evtype}]
     (a/put! out-chan event)
     receipt))
  ([this evtype data]
   (emit this nil evtype data)))

(defn listen-for-response [this {:keys [::response-chan ::error-mult] :as receipt}]
  (let [error-chan (a/promise-chan)
        _ (a/tap error-mult error-chan)
        [msg ch] (a/alts!! [response-chan error-chan])]
    (a/untap error-mult error-chan)
    (a/close! error-chan)
    (condp = ch
      response-chan (::result msg)
      error-chan    (throw (ex-info "ERROR" {:error (::error msg)})))))



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

  (defn foo-component [in-chan out-chan dep1]
    (let [dm {:foo (fn [this data]
                     (println "FOO" data)
                     (let [data    (update data :x inc)
                           receipt (emit this :bar data)
                           result  (listen-for-response this receipt)]
                       (println "FOO RESULT" result)
                       result))}]
      (-> (map->FooComponent {})
          (eventify in-chan out-chan dm))))

  (defn run-foo1 [this x]
    (emit this :foo {:x x}))


  (defrecord BarComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn bar-component [in-chan out-chan]
    (let [dm {:bar (fn [this data]
                     (println "BAR" data)
                     (let [data    (update data :x #(* % 2))
                           receipt (emit this :baz data)
                           result  (listen-for-response this receipt)]
                       (println "BAR RESULT" result)
                       result))}]
      (-> (map->BarComponent {})
          (eventify in-chan out-chan dm))))


  (defrecord BazComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn baz-component [in-chan out-chan]
    (let [dm {:baz (fn [this data]
                     (println "BAZ" data)
                     (let [result (update data :x #(/ % 3.0))]
                       (println "BAZ RESULT" result)
                       result))}]
      (-> (map->BazComponent {})
          (eventify in-chan out-chan dm))))


  (let [in-chan  (a/chan)
        in-mult  (a/mult in-chan)
        out-chan (a/chan)
        _        (a/pipe out-chan in-chan)
        fooc     (-> (foo-component in-mult out-chan :superdep1)
                     owl/start)
        barc     (-> (bar-component in-mult out-chan)
                     owl/start)
        bazc     (-> (baz-component in-mult out-chan)
                     owl/start)]
    (run-foo1 fooc 321)
    (Thread/sleep 1000)
    #_(run-foo1 fooc 123)
    #_(run-foo1 fooc 222)
    #_(Thread/sleep 1000)
    (owl/stop bazc)
    (owl/stop barc)
    (owl/stop fooc)))
