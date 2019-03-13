(ns ow.app.request-response-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

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



(defn init-responder [this name request-ch response-ch topic handler]
  (assoc this
         ::responder-config {:name name
                             :request-ch request-ch
                             :response-ch response-ch
                             :topic topic
                             :handler handler}
         ::responder-runtime {}))

(defn start-responder [{{:keys [name request-ch response-ch topic handler]} ::responder-config
                        {:keys [request-pipe]} ::responder-runtime
                        :as this}]
  (if-not request-pipe
    (let [_            (log/info "Starting request-response responder component" name)
          request-pipe (a/pipe request-ch (a/chan))
          request-pub  (a/pub request-pipe ::topic)
          request-sub  (a/sub request-pub topic (a/chan))]
      (a/go-loop [request (a/<! request-sub)]
        (if-not (nil? request)
          (do (future  ;; TODO: add some error handling
                (->> request
                     get-data
                     (handler this)
                     (new-response request)
                     (a/put! response-ch)))
              (recur (a/<! request-sub)))
          (do (a/unsub request-pub topic request-sub)
              (a/close! request-sub)
              (log/info "Stopped request-response responder component" name))))
      (assoc this ::responder-runtime {:request-pipe request-pipe}))
    this))

(defn stop-responder [{{:keys [topic]} ::responder-config
                       {:keys [request-pipe]} ::responder-runtime
                       :as this}]
  (when request-pipe
    (a/close! request-pipe))
  (assoc this ::responder-runtime {}))



(defn init-requester [this name request-ch response-ch topic]
  (assoc this
         ::requester-config {:name name
                             :request-ch request-ch
                             :response-ch response-ch
                             :topic topic}
         ::requester-runtime {}))

(defn start-requester [{{:keys [name request-ch response-ch topic]} ::requester-config
                        {:keys [response-pipe response-pub response-sub response-id-pub]} ::requester-runtime
                        :as this}]
  (if-not response-pipe
    (let [_               (log/info "Starting request-response requester component" name)
          response-pipe   (a/pipe response-ch (a/chan))
          response-pub    (a/pub response-pipe ::topic)
          response-sub    (a/sub response-pub topic (a/chan))
          response-id-pub (a/pub response-sub ::id)]
      (assoc this ::requester-runtime {:response-pipe response-pipe
                                       :response-pub response-pub
                                       :response-sub response-sub
                                       :response-id-pub response-id-pub}))
    this))

(defn stop-requester [{{:keys [topic]} ::requester-config
                       {:keys [response-pipe response-pub response-sub response-id-pub]} ::requester-runtime
                       :as this}]
  (when (and response-pub response-sub)
    (a/unsub response-pub topic response-sub)
    (a/close! response-sub))
  (when response-pipe
    (a/close! response-pipe))
  (assoc this ::requester-runtime {}))



(defn init [this name request-in-ch response-out-ch request-out-ch response-in-ch topic handler]
  (-> this
      (init-responder name request-in-ch response-out-ch topic handler)
      (init-requester name request-out-ch response-in-ch topic)))

(defn start [this]
  (-> this
      start-responder
      start-requester))

(defn stop [this]
  (-> this
      stop-requester
      stop-responder))



(defn request [{{:keys [request-ch]} ::requester-config
                {:keys [response-id-pub]} ::requester-runtime
                :as this}
               request
               & {:keys [timeout]}]
  (let [p (promise)
        req-id (get-id request)
        sub (a/sub response-id-pub req-id (a/promise-chan))]
    (a/go
      (let [timeout-ch    (a/timeout (or timeout 30000))
            [response ch] (a/alts! [sub timeout-ch])]
        (deliver p (if (= ch sub)
                     (if-not (nil? response)
                       response
                       (ex-info "response channel was closed" {:request request}))   ;; TODO: think about how to report errors in a nice way
                     (ex-info "timeout while waiting for response" {:request request})))
        (a/unsub response-id-pub req-id sub)
        (a/close! sub)))
    (a/put! request-ch request)
    p))



#_(let [topic :topic1
      req-out (a/chan)
      res-in  (a/chan)
      req-in  (a/chan)
      res-out (a/chan)
      _       (a/pipe req-out req-in)
      _       (a/pipe res-out res-in)
      c1      (-> {}
                  (init-requester "comp1" req-out res-in topic)
                  start-requester)
      c2      (-> {}
                  (init-responder "comp2" req-in res-out topic
                                  (fn [this request]
                                    (println "c2: got request" request)
                                    (Thread/sleep 500)
                                    {:bar (str (:foo request) "-bar")}))
                  start-responder)
      req1    (new-request topic {:foo "foo1"})
      req2    (new-request topic {:foo "foo2"})]
  (Thread/sleep 1000)
  (->> (request c1 req1 :timeout 10000)
       deref
       (println "user response 1:"))
  (Thread/sleep 200)
  (->> (request c1 req2 :timeout 10000)
       deref
       (println "user response 2:"))
  (Thread/sleep 1000)
  (stop-responder c2)
  (stop-requester c1)
  (a/close! res-out)
  (a/close! req-in)
  (a/close! res-in)
  (a/close! req-out))
