(ns ow.app.event-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]))

#_(defrecord Component [name id in-ch out-ch handler topic topic-fn
                      in-pipe pub sub]

  owl/Lifecycle

  (start [this]
    (if-not in-pipe
      (let [_       (log/info "Starting" name)
            in-pipe (a/pipe in-ch (a/chan))
            pub     (a/pub in-pipe topic-fn)
            sub     (a/sub pub topic (a/chan))]
        (a/go-loop [msg (a/<! sub)]
          (if-not (nil? msg)
            (do (future
                  (handler this msg))
                (recur (a/<! sub)))
            (log/info "Stopped" name)))
        (assoc this :in-pipe in-pipe :pub pub :sub sub))
      this))

  (stop [this]
    (when in-pipe
      (a/close! in-pipe))
    (when (and pub sub)
      (a/unsub pub topic sub)
      (a/close! sub))
    (assoc this :in-pipe nil :pub nil :sub nil)))

#_(defn component [name in-ch out-ch topic handler & {:keys [topic-fn]}]
  (map->Component {:name name
                   :id (rand-int Integer/MAX_VALUE)
                   :in-ch in-ch
                   :out-ch out-ch
                   :handler handler
                   :topic topic
                   :topic-fn (or topic-fn :ow.app.messaging/topic)}))



(defn init [this name in-ch out-ch topic handler & {:keys [topic-fn]}]
  (assoc this
         ::config {:name name
                   :in-ch in-ch
                   :out-ch out-ch
                   :topic topic
                   :topic-fn (or topic-fn ::topic)
                   :handler handler}
         ::runtime {}))

(defn start [{{:keys [name in-ch topic topic-fn handler]} ::config {:keys [in-pipe pub sub]} ::runtime :as this}]
  (if-not in-pipe
    (let [_       (log/info "Starting event component" name)
          in-pipe (a/pipe in-ch (a/chan))
          pub     (a/pub in-pipe topic-fn)
          sub     (a/sub pub topic (a/chan))]
      (a/go-loop [msg (a/<! sub)]
        (if-not (nil? msg)
          (do (future
                (handler this msg))
              (recur (a/<! sub)))
          (log/info "Stopped event component" name)))
      (assoc-in [this ::runtime] {:in-pipe in-pipe
                                  :pub pub
                                  :sub sub}))
    this))

(defn stop [{{:keys [topic]} ::config {:keys [in-pipe pub sub]} ::runtime :as this}]
  (when in-pipe
    (a/close! in-pipe))
  (when (and pub sub)
    (a/unsub pub topic sub)
    (a/close! sub))
  (assoc-in [this ::runtime] {}))
