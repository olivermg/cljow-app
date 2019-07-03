(ns ow.main
  (:require [clojure.string :as str]
            #_[clojure.tools.logging :as log]
            [ow.logging.api.alpha :as log]
            [ow.app :as oa]
            [signal.handler :as sig]))

(defn main [components & args]  ;; invoke this from your project's -main entrypoint
  (let [system (atom (oa/run components))]
    (sig/with-handler :int
      (log/warn "Caught INT, quitting...")
      (swap! system oa/quit))
    (sig/with-handler :term
      (log/warn "Caught TERM, quitting...")
      (swap! system oa/quit))
    (sig/with-handler :hup
      (log/warn "Caught HUP, restarting...")
      (swap! system (comp oa/run oa/quit)))
    system))
