(defproject postmortem "0.1.0-SNAPSHOT"
  :description "A tiny programmable debugging logger for Clojure(Script), powered by transducers"
  :url "https://github.com/athos/postmortem"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]]
  :profiles {:1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}})
