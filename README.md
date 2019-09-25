# Postmortem
[![Clojars Project](https://img.shields.io/clojars/v/postmortem.svg)](https://clojars.org/postmortem)
[![CircleCI](https://circleci.com/gh/athos/postmortem.svg?style=shield)](https://circleci.com/gh/athos/postmortem)
[![codecov](https://codecov.io/gh/athos/postmortem/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/postmortem)

A tiny programmable debugging logger for Clojure(Script), powered by transducers

## Installation

Add the following to your project `:dependencies`:

[![Clojars Project](https://clojars.org/postmortem/latest-version.svg)](https://clojars.org/postmortem)

## Usage

```clojure
(require '[postmortem.core :as pm])

(defn fib [n]
  (loop [n n a 0 b 1]
    (pm/save :fib)
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(fib 5) ;=> 5
(pm/log-for :fib)
;=> [{:n 5, :a 0, :b 1}
;    {:n 4, :a 1, :b 1}
;    {:n 3, :a 1, :b 2}
;    {:n 2, :a 2, :b 3}
;    {:n 1, :a 3, :b 5}
;    {:n 0, :a 5, :b 8}]

(defn fib2 [n]
  (loop [n n a 0 b 1]
    (pm/save :fib2 (filter #(even? (:n %))))
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(fib2 5) ;=> 5
(pm/log-for :fib2)
;=> [{:n 4, :a 1, :b 1} {:n 2, :a 2, :b 3} {:n 0, :a 5, :b 8}]

(require '[postmortem.xforms :as xf])

(defn fib3 [n]
  (loop [n n a 0 b 1]
    (pm/save :fib3
             (comp (map-indexed #(assoc %2 :id %1))
                   (xf/take-last 3)))
    (if (= n 0)
      a
      (recur (dec n) b (+ a b)))))

(fib3 5) ;=> 5
(pm/log-for :fib3)
;=> [{:n 2, :a 2, :b 3, :id 3} {:n 1, :a 3, :b 5, :id 4} {:n 0, :a 5, :b 8, :id 5}]

(pm/logs)
;=> {:fib [{:n 5, :a 0, :b 1}
;          {:n 4, :a 1, :b 1}
;          {:n 3, :a 1, :b 2}
;          {:n 2, :a 2, :b 3}
;          {:n 1, :a 3, :b 5}
;          {:n 0, :a 5, :b 8}]
;    :fib2 [{:n 4, :a 1, :b 1} {:n 2, :a 2, :b 3} {:n 0, :a 5, :b 8}]
;    :fib3 [{:n 2, :a 2, :b 3, :id 3} {:n 1, :a 3, :b 5, :id 4} {:n 0, :a 5, :b 8, :id 5}]}

(pm/reset!)
(pm/logs) ;=> {}
```

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
