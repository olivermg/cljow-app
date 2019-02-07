(ns ow.main
  (:gen-class)
  (:require [clojure.string :as str]
            [ow.app :as oa]))

(defn -main [& args]
  (oa/run (-> (or (System/getenv "PROFILE")
                  "prod")
              str/lower-case
              keyword)))

