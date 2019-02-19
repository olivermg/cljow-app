(ns ow.app.messaging.receiver.core-async
  (:require [clojure.core.async :as a]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.component :as owmc]
            [ow.app.messaging.receiver :as owmr]))

(defrecord CoreAsyncReceiver [in out components
                              piped-in]

  owl/Lifecycle

  (start [this]
    (if-not piped-in
      (let [piped-in (a/pipe in (a/chan))
            pub (a/pub piped-in :ow.app.messaging/type)
            xf (comp (map (fn [c]
                            [(type c) (owmc/get-dispatch-map c)]))
                     (map (fn [[t dm]]
                            (into [] (map (fn [[k h]]
                                            [t k h]))
                                  dm)))
                     (mapcat identity)
                     (map (fn [x]
                            (a/go-loop [msg (a/<! in)]
                              
                              (recur (a/<! in))))))]
        (transduce xf (constantly nil) components)
        (assoc this :piped-in piped-in))
      this))

  (stop [this]
    this)

  owmr/Receiver)

(defn core-async-receiver [in out components]
  (map->CoreAsyncReceiver {:in  in
                           :out out
                           :components components}))
