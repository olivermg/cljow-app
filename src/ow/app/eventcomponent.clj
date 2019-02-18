(ns ow.app.eventcomponent
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defn start [{{:keys [in-mult in-chan dispatch-map]} ::eventcomponent-config :as this}]
  (if-not in-chan
    (let [in-chan (a/chan)
          in-pub (-> in-mult (a/tap in-chan) (a/pub ::type))]
      (dorun
       (map (fn [[evtype handler]]
              (let [handle (fn [data {:keys [::response-chan ::error-chan] :as receipt}]
                             #_(println "HANDLE" data receipt)
                             (letfn [(handle-ex [e]
                                       (log/info (format "EXCEPTION %s in %s/%s\n >Message: %s\n >Data: %s\n >Stacktrace: %s"
                                                         (type e) (type this) (str evtype)
                                                         (.getMessage e) (pr-str data)
                                                         (with-out-str
                                                           (binding [*err* *out*]
                                                             (.printStackTrace e)))))
                                       (a/put! error-chan {::error e}))]
                               (try
                                 (let [result (handler this data)]
                                   #_(println "PUT RESPONSE" result response-chan)
                                   (a/put! response-chan {::result result}))
                                 (catch Exception e
                                   (handle-ex e))
                                 (catch Error e
                                   (handle-ex e)))))
                    sub-chan (a/chan)]
                (a/sub in-pub evtype sub-chan)
                (println "START" evtype)
                (a/go-loop [{:keys [::data ::receipt] :as msg} (a/<! sub-chan)]
                  #_(println "MSG" (select-keys msg #{::type ::data}))
                  (if-not (nil? data)
                    (do (a/thread  ;; NOTE: important to do this asynchronously to prevent deadlocks
                          (handle data receipt))
                        (recur (a/<! sub-chan)))
                    (println "STOP" evtype)))))
            dispatch-map))
      (assoc-in this [::eventcomponent-config :in-chan] in-chan))
    this))

(defn stop [{{:keys [in-mult in-chan dispatch-map]} ::eventcomponent-config :as this}]
  (if in-chan
    (do (a/untap in-mult in-chan)
        (a/close! in-chan)
        (assoc-in this [::eventcomponent-config :in-chan] nil))
    this))

(defn eventify [component in-mult out-chan dispatch-map]
  (let [eventcomponent-config {:in-mult in-mult
                               :out-chan out-chan
                               :dispatch-map dispatch-map}]
    (assoc component ::eventcomponent-config eventcomponent-config)))

(defn emit [{{:keys [out-chan]} ::eventcomponent-config :as this} evtype data]
  (let [receipt {::error-chan    (a/promise-chan)
                 ::response-chan (a/promise-chan)}
        event {::receipt receipt
               ::data    data
               ::type    evtype}]
    #_(println "EMIT" (select-keys event #{::type ::data}))
    (a/put! out-chan event)
    receipt))

(defn wait [this {:keys [::response-chan ::error-chan] :as receipt} & {:keys [timeout-ms]}]
  #_(println "WAIT 1" receipt)
  (let [timeout-chan (a/timeout (or timeout-ms 60000))
        [msg ch] (a/alts!! [response-chan error-chan timeout-chan])]
    #_(println "WAIT 2")
    (a/close! response-chan)
    (a/close! error-chan)
    (condp = ch
      response-chan (::result msg)
      error-chan    (let [e (::error msg)]
                      (throw (if (instance? Throwable e)
                               e
                               (ex-info "ERROR" {:error (::error e)
                                                 :receipt receipt}))))
      timeout-chan  (throw (ex-info "TIMEOUT" {:receipt receipt})))))



;;;
;;; TEST
;;;

#_(do

  (defrecord FooComponent []
    owl/Lifecycle
    (start [this]
      (start this))
    (stop [this]
      (stop this)))

  (defn foo-component [in-mult out-chan dep1]
    (let [dm {:foo (fn [this data]
                     #_(println "FOO" data)
                     (let [data    (update data :x inc)
                           receipt (emit this :bar data)
                           _       (emit this :mox data)
                           result  (wait this receipt)]
                       #_(println "FOO RESULT" result)
                       result))}]
      (-> (map->FooComponent {})
          (eventify in-mult out-chan dm))))

  (defn run-foo1 [this x]
    #_(Thread/sleep (rand-int 1000))
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
                     #_(println "BAR" data)
                     (let [data    (update data :x #(* % 2))
                           ;;;_ (println "BAR 2" data)
                           receipt (emit this :baz data)
                           ;;;_ (println "BAR 3" data)
                           result  (wait this receipt)]
                       #_(println "BAR RESULT" result)
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
                     #_(println "BAZ" data)
                     #_(throw (ex-info "AAAAAAARGH" {}))
                     (let [result (update data :x #(/ % 3.0))]
                       #_(println "BAZ RESULT" result)
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


  (let [in-chan-pre (a/chan)
        in-chan  (a/chan)
        _ (a/go-loop [msg (a/<! in-chan-pre)]
            (when-not (nil? msg)
              (println "PRE" (select-keys msg #{::type ::data}))
              (a/put! in-chan msg)
              (recur (a/<! in-chan-pre))))
        in-mult  (a/mult in-chan)
        out-chan (a/chan)
        _        (a/pipe out-chan in-chan-pre)
        fooc     (-> (foo-component in-mult out-chan :superdep1)
                     owl/start)
        barc     (-> (bar-component in-mult out-chan)
                     owl/start)
        bazc     (-> (baz-component in-mult out-chan)
                     owl/start)
        moxc     (-> (mox-component in-mult out-chan)
                     owl/start)]
    #_(Thread/sleep 100)
    (doall
     (pvalues
      (run-foo1 fooc 100)
      (run-foo1 fooc 1000)
      (run-foo1 fooc 10000)))
    (Thread/sleep 2000)
    (doseq [c [moxc bazc barc fooc]]
      (Thread/sleep 50)
      (owl/stop c))))



#_(let [c0 (a/chan)
      m  (a/mult c0)]
  (letfn [(emit [t]
            (let [rch (a/promise-chan)]
              #_(println "EMIT" {:type t})
              (a/put! c0 {:type t :rch rch})
              rch))
          (wait [rch]
            (let [to (a/timeout 3000)
                  [msg ch] (a/alts!! [rch to])]
              (a/close! rch)
              (condp = ch
                rch (do #_(println "RCH" msg) msg)
                to  (throw (ex-info "TIMEOUT" {:rch rch})))))]
    (doseq [t [:foo :bar :baz]]
      (let [p (-> m (a/tap (a/chan)) (a/pub :type))
            s (a/chan)]
        (println "START" t)
        (a/sub p t s)
        (a/go-loop [{:keys [type rch] :as msg} (a/<! s)]
          (if-not (nil? msg)
            (do #_(println "MSG" (dissoc msg :rch))
                (a/thread
                  (a/put! rch (case type
                                :foo (do (println "PROC FOO") (-> (emit :bar) wait))
                                :bar (do (println "PROC BAR") (-> (emit :baz) wait))
                                :baz (do (println "PROC BAZ") :endbaz))))
                (recur (a/<! s)))
            (println "STOP" t)))))
    #_(Thread/sleep 1000)
    (dorun
     (pvalues
      (->> (emit :foo) wait (println "FINAL1"))
      (->> (emit :foo) wait (println "FINAL2"))
      (->> (emit :foo) wait (println "FINAL3"))))
    (Thread/sleep 1000)
    (a/close! c0)))
