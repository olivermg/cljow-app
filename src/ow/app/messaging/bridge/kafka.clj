(ns ow.app.messaging.bridge.kafka
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl])
  (:import [java.util Arrays Properties]
           [org.apache.kafka.clients.producer ProducerConfig KafkaProducer ProducerRecord]
           [org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer ConsumerRecords]))

(defrecord KafkaOutBridge [ch topic
                           pipe producer]

  owl/Lifecycle

  (start [this]
    (if-not pipe
      (let [pipe (a/chan)
            kprops (doto (Properties.)  ;; TODO: parameterize config:
                     (.put ProducerConfig/CLIENT_ID_CONFIG "kafkaoutbridge1")
                     (.put ProducerConfig/BOOTSTRAP_SERVERS_CONFIG "kafka:9092")
                     (.put ProducerConfig/KEY_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer")
                     (.put ProducerConfig/VALUE_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"))
            kproducer (KafkaProducer. kprops)]
        (a/pipe ch pipe)
        (a/go-loop [msg (a/<! pipe)]
          (let [topic (if (fn? topic)
                        (topic msg)
                        topic)
                smsg (pr-str msg)  ;; TODO: use nippy for serialization
                ]
            (.send kproducer (ProducerRecord. topic (hash smsg) smsg))  ;; TODO: does any other key make more sense?
            (recur (a/<! pipe))))
        (assoc this :pipe pipe :producer kproducer))
      this))

  (stop [this]
    (if pipe
      (do (.close producer)
          (a/close! pipe)
          (assoc this :pipe nil :producer nil))
      this)))

(defn kafka-out-bridge [ch topic]
  (map->KafkaOutBridge {:ch ch
                        :topic topic}))


(defrecord KafkaInBridge [ch topic
                          consumer future]

  owl/Lifecycle

  (start [this]
    (if-not consumer
      (let [kprops (doto (Properties.)
                     (.put ConsumerConfig/CLIENT_ID_CONFIG "kafkainbridge1")
                     (.put ConsumerConfig/BOOTSTRAP_SERVERS_CONFIG "kafka:9092")
                     (.put ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer")
                     (.put ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"))
            kconsumer (KafkaConsumer. kprops)
            _ (.subscribe kconsumer (-> [topic] to-array Arrays/asList))
            fut (future
                  (loop [recs (.poll kconsumer 100)]
                    (doseq [rec recs]
                      (let [dmsg (-> rec .value edn/read-string)]
                        (a/put! ch dmsg)))
                    (recur (.poll kconsumer 100))))]
        (assoc this :consumer kconsumer :future fut))
      this))

  (stop [this]
    (if consumer
      (do (future-cancel future)
          (.close consumer)
          (assoc this :consumer nil :future nil))
      this)))

(defn kafka-in-bridge [ch topic]
  (map->KafkaInBridge {:ch ch
                       :topic topic}))
