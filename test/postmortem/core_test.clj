(ns postmortem.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [postmortem.core :as pm :refer [lp spy> spy>>]]))

(defn add [a b]
  (lp :add)
  (spy>> :add-result (+ a b)))

(defn fib [n]
  (loop [n n a 0 b 1]
    (lp :fib)
    (if (= n 0)
      a
      (recur (dec n) b (add a b)))))

(deftest basic-workflow-test
  (fib 5)
  (is (= [{:a 0 :b 1}
          {:a 1 :b 1}
          {:a 1 :b 2}
          {:a 2 :b 3}
          {:a 3 :b 5}]
         (pm/log-for :add)))
  (is (= [1 2 3 5 8]
         (pm/log-for :add-result)))
  (is (= [{:n 5 :a 0 :b 1}
          {:n 4 :a 1 :b 1}
          {:n 3 :a 1 :b 2}
          {:n 2 :a 2 :b 3}
          {:n 1 :a 3 :b 5}
          {:n 0 :a 5 :b 8}]
         (pm/log-for :fib)))
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          :add-result [1 2 3 5 8]}
         (pm/logs-for #{:add :add-result})))
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          :add-result [1 2 3 5 8]
          :fib [{:n 5 :a 0 :b 1}
                {:n 4 :a 1 :b 1}
                {:n 3 :a 1 :b 2}
                {:n 2 :a 2 :b 3}
                {:n 1 :a 3 :b 5}
                {:n 0 :a 5 :b 8}]}
         (pm/all-logs)))

  (pm/reset! :add-result)
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          :fib [{:n 5 :a 0 :b 1}
                {:n 4 :a 1 :b 1}
                {:n 3 :a 1 :b 2}
                {:n 2 :a 2 :b 3}
                {:n 1 :a 3 :b 5}
                {:n 0 :a 5 :b 8}]}
         (pm/all-logs)))

  (pm/reset-all!)
  (is (= {} (pm/all-logs))))
