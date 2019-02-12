(ns ow.app.eventbroker
  (:require [clojure.core.async :as a]
            [ow.app.lifecycle :as owl]))

#_(defrecord Eventbroker [in-pub out-chan dispatch-map
                        subs]

  owl/Lifecycle

  (start [this]
    (if-not subs
      (let [subs (doall
                  (map (fn [[type handler]]
                         (let [sub-chan (a/chan)]
                           (a/sub in-pub type sub-chan)
                           (a/go-loop [{:keys [::data] :as msg} (a/<! sub-chan)]
                             #_(println "DATA" data)
                             (if-not (nil? data)
                               (do (handler this data)
                                   (recur (a/<! sub-chan)))
                               (println "STOP.")))
                           [type sub-chan]))
                       dispatch-map))]
        (assoc this :subs subs))
      this))

  (stop [this]
    (if subs
      (do (dorun
           (map (fn [[type sub-chan]]
                  (a/unsub in-pub type sub-chan)
                  (a/close! sub-chan))
                subs))
          (assoc this :subs nil))
      this)))

(defn start [{{:keys [in-pub dispatch-map subs]} ::eventbroker-config :as this}]
  (if-not subs
    (let [subs (doall
                (map (fn [[type handler]]
                       (let [sub-chan (a/chan)]
                         (a/sub in-pub type sub-chan)
                         (a/go-loop [{:keys [::data] :as msg} (a/<! sub-chan)]
                           #_(println "DATA" data)
                           (if-not (nil? data)
                             (do (handler this data)
                                 (recur (a/<! sub-chan)))
                             (println "STOP.")))
                         [type sub-chan]))
                     dispatch-map))]
      (assoc-in this [::eventbroker-config :subs] subs))
    this))

(defn stop [{{:keys [in-pub dispatch-map subs]} ::eventbroker-config :as this}]
  (if subs
    (do (dorun
         (map (fn [[type sub-chan]]
                (a/unsub in-pub type sub-chan)
                (a/close! sub-chan))
              subs))
        (assoc-in this [::eventbroker-config :subs] nil))
    this))

(defn eventify [component in-chan out-chan dispatch-map]
  (let [eventbroker-config {:in-pub (a/pub in-chan ::type)
                            :out-chan out-chan
                            :dispatch-map dispatch-map}]
    (assoc component ::eventbroker-config eventbroker-config)))

(defn emit [{{:keys [out-chan]} ::eventbroker-config :as this} type data]
  (let [event {::data data
               ::type type}]
    (a/put! out-chan event)))



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
