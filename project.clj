(defproject nnm862 "0.1.0-SNAPSHOT"
  :description "Demo application"
  :url "https://github.com/sanmoskalenko/nnm862"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.673"]

                 [aero "1.1.6"]
                 [compojure "1.6.3"]
                 [clj-unifier "0.0.15"]
                 [clj-http "3.12.3"]

                 [ring/ring-defaults "0.3.4"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [ring "1.9.6"]
                 [metosin/ring-http-response "0.9.3"]
                 [metosin/muuntaja "0.6.8"]
                 [ring/ring-json "0.5.1" :exclusions [cheshire]]
                 [cheshire "5.11.0"]

                 [mount "0.1.16"]
                 [cambium/cambium.core "1.1.1"]
                 [cambium/cambium.codec-simple "1.0.0"]
                 [cambium/cambium.logback.core "0.4.5"]]

  :main nnm862.server.core

  :resource-paths ["resources" "target/resources"]

  :target-path "target/%s"

  :profiles {:dev     {:dependencies [[javax.servlet/servlet-api "2.5"]
                                      [ring/ring-mock "0.4.0"]
                                      [hashp "0.2.2"]]
                       :injections   [(require 'hashp.core)]}

             :uberjar {:aot          [nnm862.server.core]
                       :jvm-opts     ["-Dclojure.compiler.direct-linking=true"]
                       :uberjar-name "nnm862.jar"}}

  :repl-options {:init-ns nnm862.server.core})
