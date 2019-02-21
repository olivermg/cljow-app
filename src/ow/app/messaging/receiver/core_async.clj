(ns ow.app.messaging.receiver.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.component :as owmc]
            [ow.app.messaging.receiver :as owmr]))

(defrecord CoreAsyncReceiver [in out dispatch-confs
                              piped-in]

  owl/Lifecycle

  (start [this]
    (if-not piped-in
      (do (log/info "Starting CoreAsyncReceiver")
          (let [piped-in (a/pipe in (a/chan))
                pub (a/pub piped-in :ow.app.messaging/type)
                xf (map (fn [[ctype topic handler]]
                          (let [handle (fn [data {:keys [:ow.app.messaging/response-chan :ow.app.messaging/error-chan] :as receipt}]
                                         (letfn [(handle-ex [e]
                                                   (log/info (format "EXCEPTION %s in %s/%s\n >Message: %s\n >Data: %s\n >Stacktrace: %s"
                                                                     (type e) ctype topic (.getMessage e) (pr-str data)
                                                                     (with-out-str
                                                                       (binding [*err* *out*]
                                                                         (.printStackTrace e)))))
                                                   (a/put! error-chan {:ow.app.messaging/error e}))]
                                           (try
                                             (let [result (handler data)]
                                               (a/put! response-chan {:ow.app.messaging/result result}))
                                             (catch Exception e
                                               (handle-ex e))
                                             (catch Error e
                                               (handle-ex e)))))
                                sub (a/sub pub topic (a/chan))]
                            (log/info "Starting CoreAsyncReceiver message loop" {:type ctype :topic topic})
                            (a/go-loop [{:keys [:ow.app.messaging/data :ow.app.messaging/receipt] :as msg} (a/<! sub)]
                              (if-not (nil? data)
                                (do (future  ;; NOTE: important to do this asynchronously to prevent deadlocks
                                      (handle data receipt))
                                    (recur (a/<! sub)))
                                (log/info "Stopped CoreAsyncReceiver message loop" {:type ctype :topic topic}))))))]
            (transduce xf (constantly nil) dispatch-confs)
            (assoc this :piped-in piped-in)))
      this))

  (stop [this]
    (if piped-in
      (do (log/info "Stopping CoreAsyncReceiver")
          (a/close! piped-in)
          (assoc this :piped-in nil))
      this))

  owmr/Receiver)

(defn core-async-receiver [in out components]
  (owmc/validate-components components)
  (let [dispatch-xf (comp (map (fn [component]
                                 [component (owmc/get-dispatch-map component)]))
                          (map (fn [[component dispatch-map]]
                                 (into [] (map (fn [[topic handler]]
                                                 [(type component) topic (partial handler component)]))
                                       dispatch-map)))
                          (mapcat identity))]
    (map->CoreAsyncReceiver {:in  in
                             :out out
                             :dispatch-confs (into [] dispatch-xf components)})))
