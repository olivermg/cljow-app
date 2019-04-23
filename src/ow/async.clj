(ns ow.async
  (:require [clojure.core.async :as a]))

(defn partition
  "TBD"
  [ch partitioning-fn n]
  (let [chans (repeatedly n a/chan)]
    ))


(defn joining-sub
  "Subscribes to pub p on topics. Groups incoming messages via group-fn.
  Forwards messages to ch groupwise after messages for all topics within a group
  have arrived, i.e. joins the messages of all topics together before forwarding
  them to ch.

  Joined messages are being forwarded to ch in the form of a map, the specific
  topic being the key.

  :parallelism determines the number of workers per topic, defaulting to 1."
  [p topics ch group-fn & {:keys [parallelism]}]
  (let [topics      (set topics)
        parallelism (or parallelism 1)
        state-ref   (ref {})  ;; TODO: can we improve this (as it serializes processing)
        ]
    (letfn [(make-joining-xf [topic]
              (fn [rf]
                (fn
                  ([]       (rf))
                  ([result] (rf result))
                  ([result input]
                   (let [group (group-fn input)]
                     (dosync
                      (alter state-ref update group #(assoc % topic input))
                      (let [pending-msgs (get @state-ref group)]
                        (when (= (-> pending-msgs keys set)
                                 topics)
                          (alter state-ref dissoc group)
                          (rf result pending-msgs)))))))))]
      (doseq [topic topics]
        (let [sub (a/sub p topic (a/chan))
              xf  (make-joining-xf topic)]
          (a/pipeline parallelism ch xf sub)))
      ch)))



(def ch
  (let [ch (a/chan)
        p  (a/pub ch :topic)
        s  (joining-sub p #{:a :b} (a/chan) :id
                        :parallelism 4)]
    (a/go-loop [msg (a/<! s)]
      (if-not (nil? msg)
        (do (println "MSG" msg)
            (recur (a/<! s)))
        (println "QUIT")))
    ch))
(a/put! ch {:topic :a :id 11})
(a/put! ch {:topic :b :id 11})
(a/put! ch {:topic :a :id 22})
(a/put! ch {:topic :b :id 22})
(a/close! ch)
