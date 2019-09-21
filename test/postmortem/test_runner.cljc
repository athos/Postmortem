(ns postmortem.test-runner
  (:require [clojure.test :as t]
            postmortem.xforms-test
            postmortem.core-test))

(defn -main []
  (t/run-tests 'postmortem.xforms-test
               'postmortem.core-test))
