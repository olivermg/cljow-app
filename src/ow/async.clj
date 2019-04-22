(ns ow.async
  (:require [clojure.core.async :as a]))

#_(defn )

(def ch
  (letfn [(make-joining-xf [topic-fn group-fn topics]
            (let [topics (set topics)]
              (fn [rf]
                (let [t-map (volatile! {})]
                  (fn
                    ([] (rf))
                    ([result] (rf result))
                    ([result input]
                     (let [topic (topic-fn input)
                           group (group-fn input)]
                       (vswap! t-map update group #(assoc % topic input))
                       (println "STATE" @t-map)
                       (let [pending-msgs (get @t-map group)]
                         (when (= (-> pending-msgs keys set) topics)
                           (vswap! t-map dissoc group)
                           (rf result pending-msgs))))))))))]
    (let [ch (a/chan)
          p  (a/pub ch :topic)
          s1 (a/sub p :a (a/chan))
          s2 (a/sub p :b (a/chan))
          xf (make-joining-xf :topic :id #{:a :b})
          su (a/chan 1 xf)]
      (a/pipe s1 su)
      (a/pipe s2 su)
      (a/go-loop [msg (a/<! su)]
        (if-not (nil? msg)
          (do (println "MSG" msg)
              (recur (a/<! su)))
          (println "QUIT")))
      ch)))

(a/put! ch {:topic :a :id 11})
(a/put! ch {:topic :b :id 11})
(a/put! ch {:topic :a :id 22})
(a/put! ch {:topic :b :id 22})
(a/close! ch)
