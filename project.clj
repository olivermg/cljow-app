(defproject cljow-app "0.1.0-SNAPSHOT"

  :description "Handling applications & their lifecycles in a uniform way"

  :url "https://github.com/olivermg/cljow-app"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[aero "1.1.3"]
                 [digest "1.4.8"]
                 [integrant "0.7.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [trptcolin/versioneer "0.2.0"]])
