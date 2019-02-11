(ns ow.main
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ow.app :as oa]
            [signal.handler :as sig]))

(defn main [& args]  ;; invoke this from your project's -main entrypoint
  (let [profile (-> (or (System/getenv "PROFILE")
                        "prod")
                    str/lower-case
                    keyword)
        _ (log/warn (format "Starting with %s profile" profile))
        system (atom (oa/run profile))]
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
