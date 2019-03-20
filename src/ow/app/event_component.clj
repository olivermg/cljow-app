(ns ow.app.event-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.component :as owc]))

(defn new-event [topic data]
  {::topic topic
   ::data data})



(defn init-emitter [this emit-ch topic]
  (assoc this
         ::emitter-config {:emit-ch emit-ch
                           :topic topic}
         ::emitter-runtime {}))

(defn start-emitter [this]
  (log/info "Starting event emitter component" (owc/get-name this))
  this)

(defn stop-emitter [this]
  (log/info "Stopping event emitter component" (owc/get-name this))
  (assoc this ::emitter-runtime {}))



(defn init-receiver [this receive-ch topic handler]
  (assoc this
         ::receiver-config {:receive-ch receive-ch
                            :topic topic
                            :handler handler}
         ::receiver-runtime {}))

(defn start-receiver [{{:keys [receive-ch topic handler]} ::receiver-config
                       {:keys [receive-pipe]} ::receiver-runtime
                       :as this}]
  (if-not receive-pipe
    (let [_            (log/info "Starting event receiver component" (owc/get-name this))
          receive-pipe (a/pipe receive-ch (a/chan))
          receive-pub  (a/pub receive-pipe ::topic)
          receive-sub  (a/sub receive-pub topic (a/chan))]
      (a/go-loop [event (a/<! receive-sub)]
        (if-not (nil? event)
          (do (future
                (try
                  (->> event
                       ::data
                       (handler this))
                  (catch Exception e
                    (log/debug "Handler threw Exception" e)
                    (throw e))
                  (catch Error e
                    (log/debug "Handler threw Error" e)
                    (throw e))))
              (recur (a/<! receive-sub)))
          (do (a/unsub receive-pub topic receive-sub)
              (a/close! receive-sub)
              (log/info "Stopped event receiver component" (owc/get-name this)))))
      (assoc this ::receiver-runtime {:receive-pipe receive-pipe}))
    this))

(defn stop-receiver [{{:keys [receive-pipe]} ::receiver-pipe
                      :as this}]
  (when receive-pipe
    (a/close! receive-pipe))
  (assoc this ::receiver-runtime {}))



(defn init [this receive-ch emit-ch topic handler]
  (-> this
      (init-emitter emit-ch topic)
      (init-receiver receive-ch topic handler)))

(defn start [this]
  (-> this
      start-emitter
      start-receiver))

(defn stop [this]
  (-> this
      stop-receiver
      stop-emitter))



(defn emit [{{:keys [emit-ch]} ::emitter-config :as this} topic data]
  (a/put! emit-ch (new-event topic data)))



#_(let [topic1       :topic1
      topic2       :topic2
      emit-ch      (a/chan)
      receive-ch   (a/chan)
      _            (a/pipe emit-ch receive-ch)
      receive-mult (a/mult receive-ch)
      e1           (-> {}
                       (owc/init "emitter1")
                       (init-emitter emit-ch topic1)
                       start-emitter)
      r1           (-> {}
                       (owc/init "receiver1")
                       (init-receiver (a/tap receive-mult (a/chan)) topic1
                                      (fn [this event-data]
                                        (println "receiver1: got event data:" event-data)
                                        (Thread/sleep 1000)
                                        (println "receiver1: done")))
                       start-receiver)
      r2           (-> {}
                       (owc/init "receiver2")
                       (init-receiver (a/tap receive-mult (a/chan)) topic2
                                      (fn [this event-data]
                                        (println "receiver2: got event data:" event-data)
                                        (Thread/sleep 500)
                                        (println "receiver2: done")))
                       start-receiver)]
  (Thread/sleep 1000)
  (emit e1 topic1 {:foo "foo1"})
  (Thread/sleep 100)
  (emit e1 topic1 {:foo "foo2"})
  (Thread/sleep 100)
  (emit e1 topic2 {:bar "bar1"})
  (Thread/sleep 1000)
  (stop-receiver r2)
  (stop-receiver r1)
  (stop-emitter e1)
  (a/close! receive-ch)
  (a/close! emit-ch))
