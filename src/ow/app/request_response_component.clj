(ns ow.app.request-response-component
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn init [this name request-ch response-ch]
  (assoc this
         ::config {:name name
                   :request-ch request-ch
                   :response-ch response-ch}
         ::runtime {}))

(defn start []
  )

(defn stop []
  )
