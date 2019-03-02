(ns ow.app.messaging.bridge.kafka
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl])
  (:import [org.apache.kafka.clients.producer ProducerConfig KafkaProducer ProducerRecord]))

(defrecord KafkaOutBridge []

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this))

(defn kafka-out-bridge []
  (map->KafkaOutBridge {}))


(defrecord KafkaInBridge []

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this))

(defn kafka-in-bridge []
  (map->KafkaInBridge {}))
