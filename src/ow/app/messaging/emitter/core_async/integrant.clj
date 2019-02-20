(ns ow.app.messaging.emitter.core-async.integrant
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.emitter.core-async :as owec])
  (:import [ow.app.messaging.emitter.core_async CoreAsyncEmitter]))

(defmethod ig/init-key :ow.app.messaging.emitter.core-async/integrant [_ {:keys [in out wait-timeout-ms] :as opts}]
  (if-not (instance? CoreAsyncEmitter opts)
    (do (log/info "Starting ow.app.messaging.emitter.core-async.CoreAsyncEmitter")
        (-> (owec/core-async-emitter in out
                                     :wait-timeout-ms wait-timeout-ms)
            owl/start))
    opts))

(defmethod ig/halt-key! :ow.app.messaging.emitter.core-async/integrant [_ this]
  (when (instance? CoreAsyncEmitter this)
    (log/info "Stopping ow.app.messaging.emitter.core-async.CoreAsyncEmitter")
    (owl/stop this)))
