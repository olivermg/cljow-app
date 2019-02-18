(ns ow.app.msgcomponent
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defrecord Msgcomponent [in-mult out-chan dispatch-map
                         in-chan]

  owl/Lifecycle

  (start* [this parent]
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
                                   (let [result (handler parent data)]
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
        (assoc this :in-chan in-chan))
      this))

  (stop* [this parent]
    (if in-chan
      (do (a/untap in-mult in-chan)
          (a/close! in-chan)
          (assoc this :in-chan nil))
      this)))

(defn msgify [parent in-mult out-chan dispatch-map]
  (let [msgcomp (map->Msgcomponent {:in-mult in-mult
                                    :out-chan out-chan
                                    :dispatch-map dispatch-map})]
    (assoc parent ::this msgcomp)))

(defn emit [{{:keys [out-chan]} ::this :as parent} evtype data]
  (let [receipt {::error-chan    (a/promise-chan)
                 ::response-chan (a/promise-chan)}
        event {::receipt receipt
               ::data    data
               ::type    evtype}]
    #_(println "EMIT" (select-keys event #{::type ::data}))
    (a/put! out-chan event)
    receipt))

(defn wait [parent {:keys [::response-chan ::error-chan] :as receipt} & {:keys [timeout-ms]}]
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

(do

  (defrecord FooComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

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
          (msgify in-mult out-chan dm))))

  (defn run-foo1 [this x]
    #_(Thread/sleep (rand-int 1000))
    (->> (emit this :foo {:x x})
         (wait this)
         (println "FINAL RESULT")))


  (defrecord BarComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

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
          (msgify in-mult out-chan dm))))


  (defrecord BazComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

  (defn baz-component [in-mult out-chan]
    (let [dm {:baz (fn [this data]
                     #_(println "BAZ" data)
                     #_(throw (ex-info "AAAAAAARGH" {}))
                     (let [result (update data :x #(/ % 3.0))]
                       #_(println "BAZ RESULT" result)
                       result))}]
      (-> (map->BazComponent {})
          (msgify in-mult out-chan dm))))


  (defrecord MoxComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

  (defn mox-component [in-mult out-chan]
    (let [dm {:mox (fn [this data]
                     (println "MOX" data)
                     #_(throw (ex-info "MOOOOOOOOOOOOOOORGH" {}))
                     (let [result (assoc data :y ::y)]
                       (println "MOX RESULT" result)
                       result))}]
      (-> (map->MoxComponent {})
          (msgify in-mult out-chan dm))))


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
