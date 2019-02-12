(ns ow.app.eventbroker
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

(defn start [{{:keys [in-pub dispatch-map subs]} ::eventbroker-config :as this}]
  (if-not subs
    (let [subs (doall
                (map (fn [[evtype handler]]
                       (let [handle (fn [data response-chan]
                                      (letfn [(handle-ex [e]
                                                (log/info (format "%s in %s/%s: %s; data: %s"
                                                                  (type e) (type this) (str evtype)
                                                                  (.getMessage e) (pr-str data)))
                                                (a/put! response-chan {::status :error
                                                                       ::result e}))]
                                        (try
                                          (let [result (handler this data)]
                                            (a/put! response-chan {::status :success
                                                                   ::result result}))
                                          (catch Exception e
                                            (handle-ex e))
                                          (catch Error e
                                            (handle-ex e)))))
                             sub-chan (a/chan)]
                         (a/sub in-pub evtype sub-chan)
                         (a/go-loop [{:keys [::data ::response-chan] :as msg} (a/<! sub-chan)]
                           (if-not (nil? data)
                             (do (handle data response-chan)
                                 (recur (a/<! sub-chan)))
                             (println "STOP.")))
                         [evtype sub-chan]))
                     dispatch-map))]
      (assoc-in this [::eventbroker-config :subs] subs))
    this))

(defn stop [{{:keys [in-pub dispatch-map subs]} ::eventbroker-config :as this}]
  (if subs
    (do (dorun
         (map (fn [[evtype sub-chan]]
                (a/unsub in-pub evtype sub-chan)
                (a/close! sub-chan))
              subs))
        (assoc-in this [::eventbroker-config :subs] nil))
    this))

(defn eventify [component in-chan out-chan dispatch-map]
  (let [eventbroker-config {:in-pub (a/pub in-chan ::type)
                            :out-chan out-chan
                            :dispatch-map dispatch-map}]
    (assoc component ::eventbroker-config eventbroker-config)))

(defn emit [{{:keys [out-chan]} ::eventbroker-config :as this} evtype data]
  (let [response-chan (a/promise-chan)
        event (-> {::response-chan response-chan
                   ::data data
                   ::type evtype}
                  #_(update ::flow-id #(or % (rand-int Integer/MAX_VALUE))))]
    (a/put! out-chan event)
    response-chan))

(defn listen-for-response [this response-chan]
  (let [{:keys [::status ::result] :as response} (a/<!! response-chan)]
    (case status
      :success result
      :error   (throw (ex-info "ERROR" {:error result})))))



;;;
;;; TEST
;;;

(defrecord MyComponent []

  owl/Lifecycle

  (start [this]
    (start this))

  (stop [this]
    (stop this)))

(defn my-component [in-chan out-chan dep1]
  (let [dm {:foo (fn [this data]
                   (println "FOO" data)
                   (emit this :bar (update data :x inc)))
            :bar (fn [this data]
                   (println "BAR" data))}]
    (-> (map->MyComponent {})
        (eventify in-chan out-chan dm))))

(defn foo [this x]
  (emit this :foo {:x x}))



(let [in-chan  (a/chan)
      out-chan (a/chan)
      _        (a/pipe out-chan in-chan)
      mc1      (-> (my-component in-chan out-chan :superdep1)
                   (doto println)
                   owl/start)]
  (foo mc1 321)
  (Thread/sleep 100)
  (foo mc1 123)
  (foo mc1 222)
  (Thread/sleep 250)
  (owl/stop mc1))
