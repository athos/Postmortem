(ns postmortem.test-runner
  (:require [clojure.test :as t]
            postmortem.core-test))

(defn -main []
  (t/run-tests 'postmortem.core-test))
