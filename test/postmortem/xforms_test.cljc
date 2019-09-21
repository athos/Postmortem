(ns postmortem.xforms-test
  (:require [postmortem.xforms :as xf]
            [clojure.test :refer [deftest are]]))

(deftest take-last-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/take-last) (range 10) [9]

    (xf/take-last 3) (range 10) [7 8 9]

    (comp (filter even?)
          (xf/take-last 3))
    (range 10)
    [4 6 8]

    (comp (xf/take-last 3)
          (filter even?))
    (range 10)
    [8]))

(deftest drop-last-test
  (are [xform coll expected]
      (= expected (into [] xform coll))
    (xf/drop-last) (range 5) [0 1 2 3]

    (xf/drop-last 3) (range 5) [0 1]

    (comp (filter even?)
          (xf/drop-last 3))
    (range 10)
    [0 2]

    (comp (xf/drop-last 3)
          (filter even?))
    (range 10)
    [0 2 4 6]))
