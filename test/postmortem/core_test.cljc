(ns postmortem.core-test
  (:require [clojure.test :refer [deftest is are testing]]
            [postmortem.core :as pm :refer [lp spy> spy>>]]
            [postmortem.xforms :as xf]))

(defn add [a b]
  (lp :add)
  (spy>> :add-result (+ a b)))

(defn fib [n]
  (loop [n n a 0 b 1]
    (lp :fib)
    (if (= n 0)
      a
      (recur (dec n) b (add a b)))))

(deftest ^:eftest/synchronized basic-workflow-test
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

(deftest ^:eftest/synchronized transducers-test
  (are [key xform expected]
      (let [f (fn [n]
                (loop [n n]
                  (lp key xform)
                  (if (= n 0)
                    n
                    (recur (dec n)))))]
        (f 10)
        (is (= expected (pm/log-for key))))

    :f1 identity [{:n 10} {:n 9} {:n 8} {:n 7} {:n 6}
                  {:n 5} {:n 4} {:n 3} {:n 2} {:n 1} {:n 0}]

    :f2 (map :n) [10 9 8 7 6 5 4 3 2 1 0]

    :f3 (comp (map :n) (xf/take-last 3)) [2 1 0]

    :f4 (comp (xf/take-last 3) (filter #(even? (:n %)))) [{:n 2} {:n 0}])

  (pm/reset-all!))

(deftest ^:eftest/synchronized session-test
  (testing "sessions can isolate multiple execution logs"
    (pm/reset-all!)
    (let [sess1 (pm/make-session)
          sess2 (pm/make-session)
          f1 (fn [n]
               (lp sess1 :f (map :n)))
          f2 (fn [n]
               (lp sess2 :f (map :n)))]
      (f1 5)
      (f2 100)
      (f1 10)
      (f2 200)
      (= {:f [5 10]} (pm/all-logs sess1))
      (= {:f [100 200]} (pm/all-logs sess2))
      (= {} (pm/all-logs))))
  (testing "sessions can hold base transducer"
    (let [sess (pm/make-session (xf/take-last 3))
          f (fn [n]
              (lp sess :f-n (map :n))
              (spy>> sess :f-ret (filter even?) (inc n)))]
      (dotimes [i 10] (f i))
      (is (= [7 8 9] (pm/log-for sess :f-n)))
      (is (= [8 10] (pm/log-for sess :f-ret))))))

#?(:clj

   (deftest ^:eftest/synchronized locking-session-test
     (let [sess (pm/make-locking-session)
           f (fn [n] (lp sess :f (comp (map-indexed #(assoc %2 :i %1))
                                       (xf/take-last))))
           futures [(future (dotimes [i 10000] (f i)))
                    (future (dotimes [i 10000] (f i)))]]
       (run! deref futures)
       (is (= 19999 (->> (pm/log-for sess :f) first :i)))))

   )
