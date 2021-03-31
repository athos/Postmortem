(defproject postmortem "0.5.0"
  :description "A tiny data-oriented debugging tool for Clojure(Script), empowered by transducers"
  :url "https://github.com/athos/Postmortem"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/clojurescript "1.10.773" :scope "provided"]
                 [net.cgrand/macrovich "0.2.1"]]
  :plugins [[lein-eftest "0.5.9"]]
  :eftest {:multithread? :vars}
  :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}})
