(defproject cljow-app "0.1.0-SNAPSHOT"

  :description "Handling applications & their lifecycles in a uniform way"

  :url "https://github.com/olivermg/cljow-app"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[aero "1.1.3"]
                 [clj-time "0.15.0"]
                 [cljow-log "0.1.0-SNAPSHOT"]
                 [cljow-system "0.1.0-SNAPSHOT"]
                 [org.clojure/core.async "0.4.490"]
                 [digest "1.4.8"]
                 [http-kit "2.4.0-alpha4"]
                 [integrant "0.7.0"]
                 #_[org.apache.kafka/kafka-clients "2.1.1"]
                 #_[org.apache.kafka/kafka-streams "2.1.1"]
                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [org.clojure/tools.logging "0.4.1"]
                 [spootnik/signal "0.2.2"]
                 [trptcolin/versioneer "0.2.0"]
                 #_[ubergraph "0.5.2"]]

  :profiles {:dev {:dependencies [[org.apache.logging.log4j/log4j-core "2.11.2"]]}}

  :pedantic? :abort)
