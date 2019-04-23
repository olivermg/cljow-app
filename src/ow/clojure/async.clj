(ns ow.clojure.async
  (:refer-clojure :rename {partition partition-clj})
  (:require [clojure.core.async :as a]))

(defn joining-sub
  "Subscribes to topics on pub p. On receiving a message, forwards it to ch
  in the form of a 2-tuple vector [topic message]."
  [p topics ch]
  (doseq [topic topics]
    (let [xf  (map (fn [msg]
                     [topic msg]))
          sub (a/sub p topic (a/chan))]
      (a/pipeline 1 ch xf sub)))
  ch)

(defn partition
  "TBD"
  [ch partition-fn n]
  (let [chans (repeatedly n a/chan)]
    ))

(defn chunking-sub
  "Subscribes to pub p on topics. Aggregates incoming messages into the form of a map,
  e.g. {topic1 message1, topic2 message2}. Different instances of such maps are being
  identified via applying chunk-fn upon a message.

  Forwards chunk-maps to ch after messages for all topics within a chunk
  have arrived, i.e. delays all incoming messages until a chunk-map can be completed
  with messages for all specified topics.

  :parallelism determines the number of workers, defaulting to 1."
  [p topics ch chunk-fn & {:keys [parallelism]}]
  (let [topics      (set topics)
        parallelism (or parallelism 1)
        state-map   (ref {})  ;; NOTE: need to manage state here (outside of xf), for when parallelism > 1
        ]
    (letfn [(joining-xf [rf]
              (fn
                ([]       (rf))
                ([result] (rf result))
                ([result [topic message :as input]]
                 (let [chunk        (chunk-fn message)
                       forward-msgs (volatile! nil)]
                   (dosync
                    (let [pending-msgs (-> state-map
                                           (alter update chunk #(assoc % topic message))
                                           (get chunk))]
                      (when (= (-> pending-msgs keys set)
                               topics)
                        (alter state-map dissoc chunk)
                        (vreset! forward-msgs pending-msgs))))
                   (when @forward-msgs
                     (rf result @forward-msgs))))))]
      (let [jsub (joining-sub p topics (a/chan))]
        (a/pipeline parallelism ch joining-xf jsub))
      ch)))



#_(def ch
  (let [ch (a/chan)
        p  (a/pub ch :topic)
        s  (chunking-sub p #{:a :b} (a/chan) :id
                         :parallelism 4)]
    (a/go-loop [msg (a/<! s)]
      (if-not (nil? msg)
        (do (println "MSG" msg)
            (recur (a/<! s)))
        (println "QUIT")))
    ch))
#_(a/put! ch {:topic :a :id 11})
#_(a/put! ch {:topic :b :id 11})
#_(a/put! ch {:topic :a :id 22})
#_(a/put! ch {:topic :b :id 22})
#_(a/close! ch)
