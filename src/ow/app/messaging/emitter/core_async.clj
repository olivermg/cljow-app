(ns ow.app.messaging.emitter.core-async
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.lifecycle :as owl]
            [ow.app.messaging.emitter :as owme]))

(defrecord CoreAsyncEmitter [in out wait-timeout-ms]

  owl/Lifecycle

  (start [this]
    this)

  (stop [this]
    this)

  owme/Emitter

  (emit [this type data]
    (let [receipt {::error-chan    (a/promise-chan)
                   ::response-chan (a/promise-chan)}
          msg {::receipt receipt
               ::data    data
               ::type    type}]
      (a/put! out msg)
      receipt))

  (wait [this {:keys [::error-chan ::response-chan] :as receipt}]
    (let [timeout-chan (a/timeout wait-timeout-ms)
          [msg ch]     (a/alts!! [response-chan error-chan timeout-chan])]
      (a/close! response-chan)
      (a/close! error-chan)
      (condp = ch
        response-chan (::result msg)
        error-chan    (let [e (::error msg)]
                        (throw (if (instance? Throwable e)
                                 e
                                 (ex-info "ERROR" {:error (::error e)
                                                   :receipt receipt}))))
        timeout-chan  (throw (ex-info "TIMEOUT" {:receipt receipt}))))))

(defn core-async-emitter [in out & {:keys [wait-timeout-ms]}]
  (map->CoreAsyncEmitter {:in in
                          :out out
                          :wait-timeout-ms (or wait-timeout-ms 10000)}))
