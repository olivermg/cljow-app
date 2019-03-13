(ns ow.app.request-response-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn init [this name request-in-ch response-out-ch request-out-ch response-in-ch topic handler]
  (assoc this
         ::config {:name name
                   :request-in-ch request-in-ch
                   :response-out-ch response-out-ch
                   :request-out-ch request-out-ch
                   :response-in-ch response-in-ch
                   :topic topic
                   :handler handler}
         ::runtime {}))

(defn start [{{:keys [name request-in-ch response-out-ch request-out-ch response-in-ch topic handler]} ::config
              {:keys [request-in-pipe request-in-pub request-in-sub
                      response-in-pipe response-in-pub response-in-sub response-in-id-pub]} ::runtime
              :as this}]
  (if-not request-in-pipe
    (let [_                  (log/info "Starting request-response component" name)
          request-in-pipe    (a/pipe request-in-ch (a/chan))
          request-in-pub     (a/pub request-in-pipe ::topic)
          request-in-sub     (a/sub request-in-pub topic (a/chan))
          response-in-pipe   (a/pipe response-in-ch (a/chan))
          response-in-pub    (a/pub response-in-pipe ::topic)
          response-in-sub    (a/sub response-in-pub topic (a/chan))
          response-in-id-pub (a/pub response-in-sub ::id)]
      (a/go-loop [request (a/<! request-in-sub)]
        (if-not (nil? request)
          (do (future
                (handler this request))
              (recur (a/<! request-in-sub)))
          (log/info "Stopped request-response component" name)))
      (assoc this ::runtime {:request-in-pipe request-in-pipe
                             :request-in-pub request-in-pub
                             :request-in-sub request-in-sub
                             :response-in-pipe response-in-pipe
                             :response-in-pub response-in-pub
                             :response-in-sub response-in-sub
                             :response-in-id-pub response-in-id-pub}))
    this))

(defn stop [{{:keys [topic]} ::config {:keys [request-pipe pub sub]} ::runtime :as this}]
  (when request-pipe
    (a/close! request-pipe))
  (when (and pub sub)
    (a/unsub pub topic sub)
    (a/close! sub))
  (assoc this ::runtime {}))



(defn get-id [request-or-response]
  (get request-or-response ::id))

(defn get-topic [request-or-response]
  (get request-or-response ::topic))

(defn get-data [request-or-response]
  (get request-or-response ::data))

(defn new-request [topic data]
  {::id (rand-int Integer/MAX_VALUE)
   ::topic topic
   ::data data})

(defn new-response [request data]
  {::id (get-id request)
   ::topic (get-topic request)
   ::data data})



(defn request [{{:keys [request-out-ch]} ::config {:keys [response-in-id-pub]} ::runtime :as this} request & {:keys [timeout]}]
  (let [p (promise)
        req-id (get-id request)
        sub (a/sub response-in-id-pub req-id (a/promise-chan))]
    (a/go
      (let [[response ch] (a/alts! [sub (a/timeout (or timeout 30000))])]
        (deliver p (if (= ch sub)
                     (if-not (nil? response)
                       response
                       (ex-info "response channel was closed" {:request request}))
                     (ex-info "timeout while waiting for response" {:request request})))
        (a/unsub response-in-id-pub req-id sub)
        (a/close! sub)))
    (a/put! request-out-ch request)
    p))

(defn respond []
  )



#_(let [req-in  (a/chan)
      res-out (a/chan)
      req-out (a/chan)
      res-in  (a/chan)
      c       (-> {}
                  (init "testcomp" req-in res-out req-out res-in :topic1 (fn [this request]
                                                                           (println "handler: got request" request)
                                                                           {:bar "bar"}))
                  start)
      req     (new-request :topic1 {:foo "foo"})]
  (a/go-loop [req (a/<! req-out)]
    (when-not (nil? req)
      (println "other: got request" req)
      (a/put! res-in (new-response req {:bar "bar"}))
      (recur (a/<! req-out))))
  (Thread/sleep 1000)
  (-> (request c req :timeout 10000)
      deref
      (println "<- response"))
  (Thread/sleep 1000)
  (stop c)
  (a/close! req-in)
  (a/close! req-out)
  (a/close! res-out)
  (a/close! res-in))
