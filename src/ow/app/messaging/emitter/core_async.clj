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
    (let [receipt {:ow.app.messaging/error-chan    (a/promise-chan)
                   :ow.app.messaging/response-chan (a/promise-chan)}
          msg {:ow.app.messaging/receipt receipt
               :ow.app.messaging/data    data
               :ow.app.messaging/type    type}]
      (a/put! out msg)
      receipt))

  (recv [this {:keys [:ow.app.messaging/error-chan :ow.app.messaging/response-chan] :as receipt}]
    (let [timeout-chan (a/timeout wait-timeout-ms)
          [msg ch]     (a/alts!! [response-chan error-chan timeout-chan])]
      (a/close! response-chan)
      (a/close! error-chan)
      (condp = ch
        response-chan (:ow.app.messaging/result msg)
        error-chan    (let [e (:ow.app.messaging/error msg)]
                        (throw (if (instance? Throwable e)
                                 e
                                 (ex-info "ERROR" {:error   (:ow.app.messaging/error e)
                                                   :receipt receipt}))))
        timeout-chan  (throw (ex-info "TIMEOUT" {:receipt receipt}))))))

(defn core-async-emitter [in out & {:keys [wait-timeout-ms]}]
  (map->CoreAsyncEmitter {:in in
                          :out out
                          :wait-timeout-ms (or wait-timeout-ms 10000)}))
