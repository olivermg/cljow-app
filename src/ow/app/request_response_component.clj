(ns ow.app.request-response-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn init [this name request-ch response-ch topic handler & {:keys [topic-fn]}]
  (assoc this
         ::config {:name name
                   :request-ch request-ch
                   :response-ch response-ch
                   :topic topic
                   :topic-fn (or topic-fn ::topic)
                   :handler handler}
         ::runtime {}))

(defn start [{{:keys [name request-ch response-ch topic topic-fn handler]} ::config {:keys [request-pipe pub sub]} ::runtime :as this}]
  (if-not request-pipe
    (let [_            (log/info "Starting request-response component" name)
          request-pipe (a/pipe request-ch (a/chan))
          pub          (a/pub request-pipe topic-fn)
          sub          (a/sub pub topic (a/chan))]
      (a/go-loop [request (a/<! sub)]
        (if-not (nil? request)
          (do (future
                (handler this request))
              (recur (a/<! sub)))
          (log/info "Stopped request-response component" name)))
      (assoc this ::runtime {:request-pipe request-pipe
                             :pub pub
                             :sub sub}))
    this))

(defn stop [{{:keys [topic]} ::config {:keys [request-pipe pub sub]} ::runtime :as this}]
  (when request-pipe
    (a/close! request-pipe))
  (when (and pub sub)
    (a/unsub pub topic sub)
    (a/close! sub))
  (assoc this ::runtime {}))
