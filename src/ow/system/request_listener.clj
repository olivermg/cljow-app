(ns ow.system.request-listener
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [ow.clojure :as owclj]
            [ow.clojure.async :as owa]
            [ow.system.util :as osu]))

;;; TODO: implement spec'd requests/events

(def ^:private ^:dynamic *request-map* {})

(defn- trace-info [this]
  (-> (select-keys *request-map* #{:id :flowid})
      (assoc :name (osu/worker-name this))))

(defn- trace-request [this msg & msgs]
  (log/trace (trace-info this) (apply str msg (interleave (repeat " ") msgs))))

(defn init-request-response-channels-xf [rf]
  (let [out-ch  (a/chan)
        in-mult (a/mult out-ch)]
    (fn
      ([] (rf))
      ([system] (rf system))
      ([system {:keys [ow.system/name ow.system/instance ow.system/request-listener] :as component}]
       (let [system    (if request-listener
                         (let [{:keys [topic-fn topic]} request-listener
                               topic-fn (or topic-fn :topic)]
                           (update-in system [:components name :worker-sub]
                                      #(or % (let [in-tap (a/tap in-mult (a/chan))
                                                   in-pub (a/pub in-tap topic-fn)]
                                               #_(owa/chunking-sub in-pub #{topic} (a/chan) :flowid)
                                               (a/sub in-pub topic (a/chan))))))
                         system)
             component (update-in component [:ow.system/requester :out-ch] #(or % out-ch))
             system    (assoc-in system [:components name :workers instance] component)]
         (rf system component))))))

(defn init-lifecycle-xf [rf]
  (letfn [(handle-exception [this e & [try]]
            (log/debug "FAILED to invoke handler"
                       {:error-message (str e)
                        :trace-info    (trace-info this)
                        :try           (or try :last)}))

          (default-retry-delay-fn [n]
            (let [n      (inc n)
                  cap    (* 30 60 1000)
                  msec   (* (Math/log10 n)
                            (* n n)
                            1000)
                  msec   (min msec cap)
                  varpct (* (/ msec 100)
                            10)
                  varrnd (- (rand-int varpct)
                            (/ varpct 2))]
              (int (+ msec varrnd))))

          (apply-handler [{{:keys [handler retry-count retry-delay-fn]} :ow.system/request-listener :as this}
                          {:keys [request response-ch] :as request-map}]
            (let [retry-count    (or (and (not response-ch) retry-count) 1)
                  retry-delay-fn (or retry-delay-fn default-retry-delay-fn)]
              (try
                (owclj/try-times retry-count
                                 (fn [try]
                                   (trace-request this "invoking handler, try" try)
                                   (handler this request))
                                 :interleave-f (fn [e try]
                                                 (handle-exception this e try)
                                                 (let [delay (retry-delay-fn try)
                                                       p     (promise)]
                                                   (a/go
                                                     (a/<! (a/timeout delay))
                                                     (deliver p true))
                                                   p)))
                (catch Exception e
                  (handle-exception this e)
                  e)
                (catch Error e
                  (handle-exception this e)
                  e))))

          (handle-response [this {:keys [response-ch] :as request-map} response]
            (cond
                ;;; 1. if caller is waiting for a response, return response to it, regardless of if it's an exception or not:
              response-ch                    (do (trace-request this "sending back handler's response")
                                                 (a/put! response-ch response))
                ;;; 2. if nobody is waiting for our response, throw exceptions:
              (instance? Throwable response) (do (trace-request this "throwing handler's exception")
                                                 (throw response))
                ;;; 3. if nobody is waiting for our response, simply evaluate to regular responses:
              true                           (do (trace-request this "discarding handler's response")
                                                 response)))

          (run-loop [this in-ch]
            (a/go-loop [request-map (a/<! in-ch)]
              (if-not (nil? request-map)
                (do (binding [*request-map* request-map]
                      #_(trace-request this "received request-map")
                      (future
                        (->> request-map
                             (apply-handler this)
                             (handle-response this request-map))))
                    (recur (a/<! in-ch))))))

          (make-lifecycle [system component-name]
            (let [worker-sub (get-in system [:components component-name :worker-sub])]
              {:start (fn request-listener-start [{{:keys [topic-fn topic]} :ow.system/request-listener :as this}]
                        (let [in-ch (a/pipe worker-sub (a/chan))]
                          (run-loop this in-ch)
                          (assoc-in this [:ow.system/request-listener :in-ch] in-ch)))
               :stop (fn request-listener-stop [this]
                       (update-in this [:ow.system/request-listener :in-ch] #(and % a/close! nil)))}))]

    (fn
      ([] (rf))
      ([system] (rf system))
      ([system {:keys [ow.system/name ow.system/instance ow.system/request-listener] :as component}]
       (let [component (if request-listener
                         (update-in component [:ow.system/lifecycles] conj (make-lifecycle system name))
                         component)]
         (rf (assoc-in system [:components name :workers instance] component) component))))))

(defn emit [{:keys [ow.system/requester] :as this} topic request]
  ;;; TODO: do we need an option to specify retry-count per event?
  (let [{:keys [out-ch]} requester
        event-map {:id      (rand-int Integer/MAX_VALUE)
                   :flowid  (get *request-map* :flowid
                                 (rand-int Integer/MAX_VALUE))
                   :topic   topic
                   :request request}]
    (binding [*request-map* event-map]
      (trace-request this "emitting event-map")
      (a/put! out-ch event-map))))

(defn request [{:keys [ow.system/requester] :as this} topic request & {:keys [timeout]}]
  (let [{:keys [out-ch]} requester
        response-ch (a/promise-chan)
        request-map {:id          (rand-int Integer/MAX_VALUE)
                     :flowid      (get *request-map* :flowid
                                       (rand-int Integer/MAX_VALUE))
                     :topic       topic
                     :request     request
                     :response-ch response-ch}
        receipt     (a/go
                      (let [timeout-ch    (a/timeout (or timeout 30000))
                            [response ch] (a/alts! [response-ch timeout-ch])]
                        (if (= ch response-ch)
                          (if-not (nil? response)
                            response
                            (ex-info "response channel was closed" {:trace-info (trace-info this)
                                                                    :request request}))
                          (ex-info "timeout while waiting for response" {:trace-info (trace-info this)
                                                                         :request request}))))]
    (binding [*request-map* request-map]
      (trace-request this "requesting request-map")
      (a/put! out-ch request-map)
      (let [response (a/<!! receipt)]
        (trace-request this "received response")
        (if-not (instance? Throwable response)
          response
          (throw response))))))



#_(let [cfg {:ca {:ow.system/request-listener {:topic          :a
                                             :topics         #{:a}
                                             :input-spec     :tbd
                                             :output-spec    :tbd
                                             :handler        (fn [this req]
                                                               (println "ca got msg" req)
                                                               (emit this :b {:bdata 1})
                                                               (emit this :c {:cdata 1}))}}

           :cb {:ow.system/request-listener {:topic          :b
                                             :topics         #{:b}
                                             :handler        (fn [this req]
                                                               (println "cb got msg" req)
                                                               (emit this :d1 {:d1data 1}))}}

           :cc {:ow.system/request-listener {:topic          :c
                                             :topics         #{:c}
                                             :output-signals [:d2]
                                             :handler        (fn [this req]
                                                               (println "cc got msg" req)
                                                               #_(emit this :d2 {:d2data 1}))}}

           :cd {:ow.system/request-listener {:topic          :d1
                                             :topics         #{:d1 :d2}
                                             :handler        (fn [this req]
                                                               (println "cd got msg" req))}}}
      system (-> (ow.system/init-system cfg)
                 (ow.system/start-system))
      ca     (get-in system [:components :ca :workers 0])]
  (emit ca :a {:adata 1})
  (Thread/sleep 1000)
  (ow.system/stop-system system))
