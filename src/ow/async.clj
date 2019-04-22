(ns ow.async
  (:require [clojure.core.async :as a]))

#_(defn )

(def ch
  (letfn [(make-joining-xf [topics]
            (let [topics (set topics)]
              (fn [rf]
                (let [t-map (volatile! {})]
                  (fn
                    ([] (rf))
                    ([result] (rf result))
                    ([result {:keys [topic id] :as input}]
                     (vswap! t-map update id #(assoc % topic input))
                     (println "STATE" @t-map)
                     (let [pending-msgs (get @t-map id)]
                       (if (= (-> pending-msgs keys set) topics)
                         (do (vswap! t-map dissoc id)
                             (rf result pending-msgs))))))))))]
    (let [ch (a/chan)
          p  (a/pub ch :topic)
          s1 (a/sub p :a (a/chan))
          s2 (a/sub p :b (a/chan))
          su (a/chan 1 (make-joining-xf #{:a :b}))]
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
