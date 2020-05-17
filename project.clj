(defproject nancy.crow/eve-wallet-clj "0.1.0-SNAPSHOT"
  :description "Eve API (ESI) wallet wrapper and helpers"
  :url "https://github.com/shenley/eve-wallet-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.stuartsierra/component "0.3.2"]
                 [clj-http "3.10.1"]
                 [org.immutant/web "2.1.7"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-json "0.5.0"]
                 [compojure "1.6.1"]
                 [com.taoensso/timbre "4.10.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [com.stuartsierra/component.repl "0.2.0"]]
                   :source-paths ["dev"]}})
