# Postmortem
[![Clojars Project](https://img.shields.io/clojars/v/postmortem.svg)](https://clojars.org/postmortem)
[![CircleCI](https://circleci.com/gh/athos/postmortem.svg?style=shield)](https://circleci.com/gh/athos/postmortem)
[![codecov](https://codecov.io/gh/athos/postmortem/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/postmortem)

A tiny value-oriented debugging logger for Clojure(Script), powered by transducers

## Features

- Postmortem encourages data-driven approaches in the debugging process
- Integration with transducers enables various flexible logging strategies
- Instrumentation on vars makes it easier to debug functions without touching their code
- Supports Clojure/ClojureScript/self-hosted ClojureScript
- Possible to use for debugging multi-threaded programs

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
  - [Basic usage](#basic-usage)
    - [`spy>>` / `log-for`](#spy--log-for)
    - [`reset-for!` / `completed?`](#reset-for--completed)
    - [`logs` / `reset!`](#logs--reset)
    - [`spy>`](#spy)
    - [`dump`](#dump)
  - [Integration with transducers](#integration-with-transducers)
  - [Sessions](#sessions)
    - `current-session`
    - `make-session`
    - `set-current-session!`
    - `with-session`
    - `void-session`
    - `make-unsafe-session`
  - [Instrumentation](#instrumentation)
    - `instrument` & `unstrument`
- [Related works](#related-works)

## Requirements

- Clojure 1.8+, or
- ClojureScript 1.10.238+, or
- Planck 2.24.0+, or
- Lumo 1.10.1+

## Installation

Add the following to your project `:dependencies`:

[![Clojars Project](https://clojars.org/postmortem/latest-version.svg)](https://clojars.org/postmortem)

## Usage

### Basic usage

#### `spy>>` / `log-for`

In Postmortem, `spy>>` and `log-for` are two of the most fundamental functions.
`spy>>` is for logging data, and `log-for` is for retrieving logged data.

`spy>>` is used as follows:

```clojure
(require '[postmortem.core :as pm])

(defn sum [n]
  (loop [i 0 sum 0]
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum (+ i sum))))))
```

`(spy>> <key> <expr>)` stores the value of the `<expr>` to a log entry for the key `<key>`
each time the `spy>>` form is evaluated. In the above example, `(pm/spy>> :sum (+ i sum))`
will store intermediate results of summation for each iteration to the log entry for
the key `:sum`.

`(log-for <key>)`, on the other hand, retrieves all the logged data stored
in the log entry for the key `<key>`. In the following example, `(log-for :sum)` results in
`[0 1 3 6 10 15]`, which is the intermediate summations from 0 to 5:

```clojure
(sum 5)
;=> 15

(pm/log-for :sum)
;=> [0 1 3 6 10 15]
```

Any Clojure data can be used as a log entry key, such as keywords (as in the above examples),
symbols, integers, strings or whatever .
In fact, you can even use a runtime value as a key, not only a literal value, and
thus log entry keys can be used to collect and group the log data:

```clojure
(defn f [n]
  (pm/spy>> [:f (even? n)] (inc n)))

(mapv f [1 2 3 4 5])
;=> [2 3 4 5 6]
(pm/log-for [:f true])
;=> [3 5]
(pm/log-for [:f false])
;=> [2 4 6]
```

#### `reset-for!` / `completed?`

To clear the logged data at the log entry `<key>`, call `(reset-for! <key>)`:

```clojure
(pm/log-for :sum)
;=> [0 1 3 6 10 15]

(pm/reset-for! :sum)

(pm/log-for :sum)
;=> nil
```

Note that once you call `log-for` for a key `k`, the log entry for `k` will be *completed*.
A completed log entry will not be changed anymore until you call `reset-for!` for the log entry `k`:

```clojure
(pm/spy>> :foobar 1)
(pm/spy>> :foobar 2)
(pm/log-for :foobar)
;=> [1 2]

(pm/spy>> :foobar 3)
(pm/spy>> :foobar 4)
(pm/log-for :foobar)
;=> [1 2]

(pm/reset-for! :foobar)

(pm/spy>> :foobar 3)
(pm/spy>> :foobar 4)
(pm/log-for :foobar)
;=> [3 4]
```

You can check if a log entry has been completed using `(completed? <key>)`:

```clojure
(pm/spy>> :barbaz 10)
(pm/spy>> :barbaz 20)
(pm/completed? :barbaz)
;=> false

(pm/log-for :barbaz)
;=> [10 20]
(pm/completed? :barbaz)
;=> true

(pm/reset-for! :barbaz)
(pm/completed? :barbaz)
;=> false
```

#### `logs` / `reset!`

You can also logs some data to more than one log entries at once.
In such a case, `logs` is more useful to look into the whole log data
than just calling `log-for` for each log entry:

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/spy>> :i i)
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum (+ i sum))))))

(sum 5)
;=> 15

(pm/logs)
;=> {:i [0 1 2 3 4 5 6],
;    :sum [0 1 3 6 10 15]}
```

Similarly, `reset!` is useful to clear the whole log data at a time, rathar than
clearing each individual log entry one by one calling `reset-for!`:

```clojure
(pm/logs)
;=> {:i [0 1 2 3 4 5 6],
;    :sum [0 1 3 6 10 15]}

(pm/reset!)

(pm/logs)
;=> {}
```

#### `spy>`

`spy>>` has a look-alike cousin called `spy>`. They have no semantic difference,
except that `spy>` is primarily intended to be used in *thread-first* contexts
and therefore takes the log data as its *first* argument while `spy>>` is mainly
intended to be used in *thread-last* contexts and therefore takes the log data
as its *last* argument.

For example, the following two expressions behave in exactly the same way:

```clojure
;; thread-last version
(->> (+ 1 2)
     (pm/spy>> :sum)
     (* 10)
     (pm/spy>> :prod))

;; thread-first version
(-> (+ 1 2)
    (pm/spy> :sum)
    (* 10)
    (pm/spy> :prod))
```

#### `dump`

`dump` is another convenient tool to take snapshots of the values of local bindings.

`(dump <key>)` stores local environment maps to the log entry `<key>`.
A *local environment map* is a map of keywords representing local names in the scope
at the callsite, to the value that the corresponding local name is bound to.

The example code below shows how `dump` logs the values of the local bindings
at the callsite (namely, `n`, `i` and `sum`) for each iteration:

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum)
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 5)
;=> 15

(pm/log-for :sum)
;=> [{:n 5, :i 0, :sum 0}
;    {:n 5, :i 1, :sum 0}
;    {:n 5, :i 2, :sum 1}
;    {:n 5, :i 3, :sum 3}
;    {:n 5, :i 4, :sum 6}
;    {:n 5, :i 5, :sum 10}
;    {:n 5, :i 6, :sum 15}]
```

### Integration with transducers

After reading this document so far, you may wonder what if the loop would be repeated millions of times?

That's where Postmortem really shines. It achieves extremely flexible customization of logging strategies
by integration with transducers. For details on transducers, see
the [official reference](https://clojure.org/reference/transducers).

Postmortem's logging operators (`spy>>`, `spy>` and `dump`) are optionally takes a transducer
after the log entry key. When you call `(spy>> <key> <xform> <expr>)`, the transducer `<xform>`
controls when the given data will be logged and/or what data will actually be stored.

For example:

```clojure
(defn sum1 [n]
  (loop [i 0 sum 0]
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum1 (+ i sum))))))

(defn sum2 [n]
  (loop [i 0 sum 0]
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum2 (filter odd?) (+ i sum))))))

(sum1 5) ;=> 15
(sum2 5) ;=> 15

(pm/log-for :sum1)
;=> [0 1 3 6 10 15]

(pm/log-for :sum2)
;=> [1 3 15]
```

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum (map (fn [m] (select-keys m [:sum]))))
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 5) ;=> 15

(pm/log-for :sum)
;=> [{:sum 0}
;    {:sum 0}
;    {:sum 1}
;    {:sum 3}
;    {:sum 6}
;    {:sum 10}
;    {:sum 15}]
```

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum (take 3))
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 5) ;=> 15

(pm/log-for :sum)
;=> [{:n 5, :i 0, :sum 0} {:n 5, :i 1, :sum 0} {:n 5, :i 2, :sum 1}]
```

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum (drop-while (fn [{:keys [sum]}] (< sum 5))))
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 5) ;=> 15

(pm/log-for :Sum)
;=> [{:n 5, :i 4, :sum 6} {:n 5, :i 5, :sum 10} {:n 5, :i 6, :sum 15}]
```

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum (random-sample 0.3))
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 5) ;=> 15

(pm/log-for :sum)
;=> [{:n 5, :i 0, :sum 0} {:n 5, :i 3, :sum 3}]

(pm/reset!)
(sum 5) ;=> 15

(pm/log-for :sum)
;=> [{:n 5, :i 2, :sum 1} {:n 5, :i 4, :sum 6} {:n 5, :i 5, :sum 10}]
```

### Sessions

### Instrumentation

## Related works

- [scope-capture](https://github.com/vvvvalvalval/scope-capture)
- [miracle.save](https://github.com/Saikyun/miracle.save)

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
