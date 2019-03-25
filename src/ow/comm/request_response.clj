(ns ow.comm.request-response
  (:require [clojure.tools.logging :as log]
            [ow.lifecycle :as owl]
            [ow.comm.request-response.requester :as req]
            [ow.comm.request-response.responder :as res]))

(defn construct [responder-in-ch responder-out-ch requester-out-ch requester-in-ch handler]
  (owl/construct ::request-response {:requester (req/construct requester-out-ch requester-in-ch)
                                     :responder (res/construct responder-in-ch responder-out-ch handler)}))

(defmethod owl/start ::request-resopnse [this]
  (-> this
      (update :requester owl/start)
      (update :responder owl/start)))

(defmethod owl/stop ::reqeust-response [this]
  (-> this
      (update :responder owl/stop)
      (update :requester owl/stop)))
