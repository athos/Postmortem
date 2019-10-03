(ns example.core
  (:require [postmortem.core :as pm :refer [dump]]))

(defn fib
  ([n] (fib n 0 1))
  ([n a b]
   (dump :fib)
   (if (= n 0)
     a
     (recur (dec n) b (+ a b)))))

(defn fib' [n]
  (loop [n n a 0 b 1]
    (dump :fib2 (comp (map #(dissoc % :n)) (take 5)))
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(declare my-odd?)

(defn my-even? [n]
  (dump :my-even? (filter #(> (:n %) 5)))
  (if (= n 0)
    true
    (my-odd? (dec n))))

(defn my-odd? [n]
  (dump :my-odd? (map :n))
  (if (= n 0)
    false
    (my-even? (dec n))))

(comment

  (fib' 5)
  ;=> 5
  (pm/logs)
  ;=> [{:a 0, :b 1} {:a 1, :b 1} {:a 1, :b 2} {:a 2, :b 3} {:a 3, :b 5}]

  )
