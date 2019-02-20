(ns ow.app.messaging.receiver.core-async.integrant
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.receiver.core-async :as owrc])
  (:import [ow.app.messaging.receiver.core_async CoreAsyncReceiver]))

(defmethod ig/init-key :ow.app.messaging.receiver.core-async/integrant [_ {:keys [in out components] :as opts}]
  (if-not (instance? CoreAsyncReceiver opts)
    (do (log/info "Starting ow.app.messaging.receiver.core-async.CoreAsyncReceiver")
        (-> (owrc/core-async-receiver in out components)
            owl/start))
    opts))

(defmethod ig/halt-key! :ow.app.messaging.receiver.core-async/integrant [_ this]
  (when (instance? CoreAsyncReceiver this)
    (log/info "Stopping ow.app.messaging.receiver.core-async.CoreAsyncReceiver")
    (owl/stop this)))
