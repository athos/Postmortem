(ns example.core
  (:require [postmortem.core :as pm]))

(defn fib
  ([n] (fib n 0 1))
  ([n a b]
   (pm/checkpoint :fib)
   (if (= n 0)
     a
     (recur (dec n) b (+ a b)))))

(defn fib' [n]
  (loop [n n a 0 b 1]
    (pm/checkpoint :fib2 (pm/except :n) (pm/first 5))
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(declare my-odd?)

(defn my-even? [n]
  (pm/checkpoint :my-even? nil (pm/when #(> (:n %) 5) (pm/all)))
  (if (= n 0)
    true
    (my-odd? (dec n))))

(defn my-odd? [n]
  (pm/checkpoint :my-odd? #{:n})
  (if (= n 0)
    false
    (my-even? (dec n))))
