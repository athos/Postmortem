(ns postmortem.xforms-test
  (:require [clojure.test :refer [are deftest]]
            [postmortem.xforms :as xf]))

(deftest take-until-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/take-until even?)
    [1 3 5 6 7 8]
    [1 3 5 6]

    (xf/take-until zero?)
    [3 2 1 0 1 2 3 0]
    [3 2 1 0]

    (xf/take-until odd?)
    [2 4 6 8]
    [2 4 6 8]

    (xf/take-until zero?)
    [3 2 1 0]
    [3 2 1 0]

    (xf/take-until zero?)
    []
    []))

(deftest drop-until-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/drop-until even?)
    [1 3 5 6 7 8]
    [7 8]

    (xf/drop-until zero?)
    [3 2 1 0 1 2 3 0]
    [1 2 3 0]

    (xf/drop-until odd?)
    [1 3 5 7]
    [3 5 7]

    (xf/drop-until zero?)
    [3 2 1]
    []

    (xf/drop-until zero?)
    []
    []))

(deftest take-last-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/take-last) (range 10) [9]

    (xf/take-last 3) (range 10) [7 8 9]

    (xf/take-last 3) (range 2) [0 1]

    (comp (filter even?)
          (xf/take-last 3))
    (range 10)
    [4 6 8]

    (comp (xf/take-last 3)
          (filter even?))
    (range 10)
    [8]

    (comp (xf/take-last 3)
          (xf/take-last 2))
    (range 10)
    [8 9]

    (comp (xf/take-last 3)
          (xf/take-last 2))
    []
    []))

(deftest drop-last-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/drop-last) (range 5) [0 1 2 3]

    (xf/drop-last 3) (range 5) [0 1]

    (xf/drop-last 3) (range 2) []

    (comp (filter even?)
          (xf/drop-last 3))
    (range 10)
    [0 2]

    (comp (xf/drop-last 3)
          (filter even?))
    (range 10)
    [0 2 4 6]

    (comp (xf/drop-last 3)
          (xf/drop-last 2))
    (range 10)
    [0 1 2 3 4]

    (comp (xf/drop-last 3)
          (xf/drop-last 2))
    []
    []))

(deftest dedupe-by-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/dedupe-by :x)
    []
    []

    (xf/dedupe-by :x)
    [{:x 0}]
    [{:x 0}]

    (xf/dedupe-by :x)
    [{:x 0 :y 1} {:x 0 :y 2} {:x 1 :y 2} {:x 4 :y 3} {:x 4 :y 5}]
    [{:x 0 :y 1} {:x 1 :y 2} {:x 4 :y 3}]

    (xf/dedupe-by identity)
    [1 1 2 4 4 4 5]
    [1 2 4 5]))

(deftest debounce-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/debounce 3)
    [1 2 4 5 8 10 11]
    [1 4 8 11]

    (xf/debounce 3)
    [1 2 4 3 0 1 5]
    [1 4 0 5]

    (xf/debounce :t 5)
    [{:t 0} {:t 3} {:t 4} {:t 5} {:t 8} {:t 11} {:t 16}]
    [{:t 0} {:t 5} {:t 11} {:t 16}]))
