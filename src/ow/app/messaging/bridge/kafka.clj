(ns ow.app.messaging.bridge.kafka
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl])
  (:import [java.util Arrays Properties]
           [org.apache.kafka.clients.producer ProducerConfig KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer ConsumerRecords]))

(defrecord KafkaOutBridge [ch topic
                           pipe]

  owl/Lifecycle

  (start [this]
    (if-not pipe
      (let [pipe (a/pipe ch (a/chan))
            kprops (doto (Properties.)  ;; TODO: parameterize config:
                     (.put ProducerConfig/CLIENT_ID_CONFIG "kafkaoutbridge1")
                     (.put ProducerConfig/BOOTSTRAP_SERVERS_CONFIG "kafka:9092")
                     (.put ProducerConfig/KEY_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer")
                     (.put ProducerConfig/VALUE_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"))
            kproducer (KafkaProducer. kprops)]
        (a/go-loop [msg (a/<! pipe)]
          (if-not (nil? msg)
            (do (println "msg in" msg)
                (let [topic (if (fn? topic)
                              (topic msg)
                              topic)
                      smsg (pr-str msg)  ;; TODO: use nippy for serialization
                      ]
                  (println "sending" smsg)
                  (.send kproducer (ProducerRecord. topic (-> smsg hash str) smsg))  ;; TODO: does any other key make more sense?
                  (recur (a/<! pipe))))
            (do (.close kproducer)
                (println "KafkaOutBridge stopped."))))
        (assoc this :pipe pipe))
      this))

  (stop [this]
    (if pipe
      (do (a/close! pipe)
          (assoc this :pipe nil))
      this)))

(defn kafka-out-bridge [ch topic]
  (map->KafkaOutBridge {:ch ch
                        :topic topic}))


(defrecord KafkaInBridge [ch topic run?
                          future*]

  owl/Lifecycle

  (start [this]
    (if-not future*
      (let [kprops (doto (Properties.)
                     (.put ConsumerConfig/CLIENT_ID_CONFIG "kafkainbridge1")
                     (.put ConsumerConfig/GROUP_ID_CONFIG "kafkabridgegroup")
                     (.put ConsumerConfig/BOOTSTRAP_SERVERS_CONFIG "kafka:9092")
                     (.put ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringDeserializer")
                     (.put ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringDeserializer"))
            kconsumer (KafkaConsumer. kprops)
            _ (reset! run? true)
            fut (future
                  (.subscribe kconsumer [topic])
                  (loop [recs (.poll kconsumer 100)]
                    (doseq [rec recs]
                      (let [dmsg (-> rec .value edn/read-string)]
                        (println "recv msg" dmsg)
                        (a/put! ch dmsg)))
                    (if @run?
                      (recur (.poll kconsumer 100))
                      (do (.close kconsumer)
                          (println "KafkaInBridge stopped.")))))]
        (assoc this :future* fut))
      this))

  (stop [this]
    (if future*
      (do (reset! run? false)
          (assoc this :future* nil))
      this)))

(defn kafka-in-bridge [ch topic]
  (map->KafkaInBridge {:ch ch
                       :topic topic
                       :run? (atom false)}))
