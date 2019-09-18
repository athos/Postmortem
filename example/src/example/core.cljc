(ns example.core
  (:require [postmortem.core :as pm :refer [lp]]))

(defn fib
  ([n] (fib n 0 1))
  ([n a b]
   (lp :fib)
   (if (= n 0)
     a
     (recur (dec n) b (+ a b)))))

(defn fib' [n]
  (loop [n n a 0 b 1]
    (lp :fib2 (comp (map #(dissoc % :n)) (take 5)))
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(declare my-odd?)

(defn my-even? [n]
  (lp :my-even? (filter #(> (:n %) 5)))
  (if (= n 0)
    true
    (my-odd? (dec n))))

(defn my-odd? [n]
  (lp :my-odd? (map :n))
  (if (= n 0)
    false
    (my-even? (dec n))))
