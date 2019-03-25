(ns ow.comm.request-response.requester
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]))

(defn construct [out-ch in-ch]
  (owl/construct ::requester {:out-ch out-ch
                              :in-ch in-ch}))

(defn request [{:keys [out-ch in-ch] :as this} data]
  (let [response-ch (a/promise-chan)]
    ))
