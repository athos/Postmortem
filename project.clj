(defproject postmortem "0.5.1"
  :description "A tiny data-oriented debugging tool for Clojure(Script), powered by transducers"
  :url "https://github.com/athos/Postmortem"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1" :scope "provided"]
                 [org.clojure/clojurescript "1.11.57" :scope "provided"]
                 [net.cgrand/macrovich "0.2.1"]]
  :plugins [[lein-eftest "0.5.9"]]
  :eftest {:multithread? :vars}
  :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}})
