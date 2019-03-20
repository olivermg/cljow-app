(ns ow.app.request-response-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.app.component :as owc]))

(defn new-request [topic data]
  {::id (rand-int Integer/MAX_VALUE)
   ::type :request
   ::topic topic
   ::data data})

(defn new-response [request data]
  {::id (::id request)
   ::type :response
   ::topic (::topic request)
   ::data data})



(defn init-responder [this request-ch response-ch topic handler]
  (assoc this
         ::responder-config {:request-ch request-ch
                             :response-ch response-ch
                             :topic topic
                             :handler handler}
         ::responder-runtime {}))

(defn start-responder [{{:keys [request-ch response-ch topic handler]} ::responder-config
                        {:keys [request-pipe]} ::responder-runtime
                        :as this}]
  (if-not request-pipe
    (let [_            (log/info "Starting request-response responder component" (owc/get-name this))
          request-pipe (a/pipe request-ch (a/chan))
          request-pub  (a/pub request-pipe (fn [req] [(::type req) (::topic req)]))
          request-sub  (a/sub request-pub [:request topic] (a/chan))]
      (a/go-loop [request (a/<! request-sub)]
        (if-not (nil? request)
          (do (future
                (->> (try
                       (->> request
                            ::data
                            (handler this))
                       (catch Exception e
                         (log/debug "Handler threw Exception" e)
                         e)
                       (catch Error e
                         (log/debug "Handler threw Error" e)
                         e))
                     (new-response request)
                     (a/put! response-ch)))
              (recur (a/<! request-sub)))
          (do (a/unsub request-pub topic request-sub)
              (a/close! request-sub)
              (log/info "Stopped request-response responder component" (owc/get-name this)))))
      (assoc this ::responder-runtime {:request-pipe request-pipe}))
    this))

(defn stop-responder [{{:keys [request-pipe]} ::responder-runtime
                       :as this}]
  (when request-pipe
    (a/close! request-pipe))
  (assoc this ::responder-runtime {}))



(defn init-requester [this request-ch response-ch]
  (assoc this
         ::requester-config {:request-ch request-ch
                             :response-ch response-ch}
         ::requester-runtime {}))

(defn start-requester [{{:keys [request-ch response-ch]} ::requester-config
                        {:keys [response-pipe]} ::requester-runtime
                        :as this}]
  (if-not response-pipe
    (let [_             (log/info "Starting request-response requester component" (owc/get-name this))
          response-pipe (a/pipe response-ch (a/chan))
          response-mult (a/mult response-pipe)]
      (assoc this ::requester-runtime {:response-pipe response-pipe
                                       :response-mult response-mult}))
    this))

(defn stop-requester [{{:keys [response-pipe]} ::requester-runtime
                       :as this}]
  (when response-pipe
    (log/info "Stopping request-response requester component" (owc/get-name this))
    (a/close! response-pipe))
  (assoc this ::requester-runtime {}))



(defn init [this request-in-ch response-out-ch request-out-ch response-in-ch topic handler]
  (-> this
      (init-responder request-in-ch response-out-ch topic handler)
      (init-requester request-out-ch response-in-ch)))

(defn start [this]
  (-> this
      start-responder
      start-requester))

(defn stop [this]
  (-> this
      stop-requester
      stop-responder))



(defn request [{{:keys [request-ch]} ::requester-config
                {:keys [response-mult]} ::requester-runtime
                :as this}
               topic
               data
               & {:keys [timeout]}]
  (let [{:keys [::id] :as request} (new-request topic data)
        tap       (a/tap response-mult (a/chan))
        pub       (a/pub tap (fn [{:keys [::id ::type ::topic]}] [type topic id]))
        sub-topic [:response topic id]
        sub       (a/sub pub sub-topic (a/promise-chan))
        receipt   (a/go
                    (let [timeout-ch    (a/timeout (or timeout 30000))
                          [response ch] (a/alts! [sub timeout-ch])
                          response-data (if (= ch sub)
                                          (if-not (nil? response)
                                            (::data response)
                                            (ex-info "response channel was closed" {:request request}))
                                          (ex-info "timeout while waiting for response" {:request request}))]
                      (a/unsub pub sub-topic sub)
                      (a/untap response-mult tap)
                      (a/close! sub)
                      (a/close! tap)
                      response-data))]
    (a/put! request-ch request)
    receipt))

(defn wait-for-response [receipt]
  (let [response-data (a/<!! receipt)]
    (if-not (instance? Throwable response-data)
      response-data
      (throw response-data))))



#_(do (import [java.util Date])
    (let [topic1  :topic1
          topic2  :topic2
          req-out (a/chan)
          res-in  (a/chan)
          req-in  (a/chan)
          res-out (a/chan)
          _       (a/pipe req-out req-in)
          _       (a/pipe res-out res-in)
          req-in-mult (a/mult req-in)
          res-in-mult (a/mult res-in)
          c1      (-> {}
                      (owc/init "comp1")
                      (init-requester req-out (a/tap res-in-mult (a/chan)))
                      start-requester)
          c2      (-> {}
                      (owc/init "comp2")
                      (init-responder (a/tap req-in-mult (a/chan)) res-out topic1
                                      (fn [this request]
                                        (println "c2: got request:" request)
                                        (Thread/sleep 500)
                                        {:bar (str (:foo request) "-bar")}))
                      start-responder)
          c3      (-> {}
                      (owc/init "comp3")
                      (init-responder (a/tap req-in-mult (a/chan)) res-out topic2
                                      (fn [this request]
                                        (println "c3: got request:" request)
                                        (Thread/sleep 300)
                                        {:baz (str (:foo request) "-baz")}))
                      start-responder)]
      (Thread/sleep 1000)
      (println "pre req1:" (Date.))
      (doseq [v (pvalues (-> (request c1 topic1 {:foo "foo11"} :timeout 10000) wait-for-response)
                         (-> (do (Thread/sleep 100) (request c1 topic2 {:foo "foo12"} :timeout 10000)) wait-for-response))]
        (println "v1:" v))
      (println "post req1:" (Date.))
      (Thread/sleep 200)
      (println "\n=====\npre req2:" (Date.))
      (doseq [v (pvalues (-> (request c1 topic1 {:foo "foo21"} :timeout 10000) wait-for-response)
                         (-> (do (Thread/sleep 100) (request c1 topic2 {:foo "foo22"} :timeout 10000)) wait-for-response))]
        (println "v2:" v))
      (println "post req2:" (Date.))
      (Thread/sleep 1000)
      (stop-responder c3)
      (stop-responder c2)
      (stop-requester c1)
      (a/close! res-out)
      (a/close! req-in)
      (a/close! res-in)
      (a/close! req-out)))
