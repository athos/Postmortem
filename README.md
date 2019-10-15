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

- Prerequisites
- Installation
- Usage
  - Basic usage
    - `spy>>` / `log-for` / `reset~for!` / `completed?`
    - `logs` / `reset!`
    - `spy>`
    - `dump`
  - Integration with transducers
  - Sessions
    - `current-session`
    - `make-session`
    - `set-current-session!`
    - `with-session`
    - `void-session`
    - `make-unsafe-session`
  - Instrumentation
    - `instrument` & `unstrument`
- Related works

## Prerequisites

- Clojure 1.8+, or
- ClojureScript 1.10.238+, or
- Planck 2.24.0+, or
- Lumo 1.10.1+

## Installation

Add the following to your project `:dependencies`:

[![Clojars Project](https://clojars.org/postmortem/latest-version.svg)](https://clojars.org/postmortem)

## Usage

### Basic usage

#### `spy>>` / `log-for` / `reset-for!` / `completed?`

```clojure
(require '[postmortem.core :as pm])

(defn sum [n]
  (loop [i 0 sum 0]
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum (+ i sum))))))
```

```clojure
(sum 10)
;=> 55

(pm/log-for :sum)
;=> [0 1 3 6 10 15 21 28 36 45 55]

(pm/reset-for! :sum)

(pm/log-for :sum)
;=> nil
```

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

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/spy>> :i i)
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum (+ i sum))))))

(sum 10)
;=> 55

(pm/logs)
;=> {:i [0 1 2 3 4 5 6 7 8 9 10 11],
;    :sum [0 1 3 6 10 15 21 28 36 45 55]}

(pm/reset!)

(pm/logs)
;=> {}
```

#### `spy>`

```clojure
(->> (+ 1 2)
     (pm/spy>> :sum)
     (* 10)
     (pm/spy>> :prod))

(-> (+ 1 2)
    (pm/spy> :sum)
    (* 10)
    (pm/spy> :prod))
```

#### `dump`

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum)
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 10)
;=> 55

(pm/log-for :sum)
;=> [{:n 10, :i 0, :sum 0}
;    {:n 10, :i 1, :sum 0}
;    {:n 10, :i 2, :sum 1}
;    {:n 10, :i 3, :sum 3}
;    {:n 10, :i 4, :sum 6}
;    {:n 10, :i 5, :sum 10}
;    {:n 10, :i 6, :sum 15}
;    {:n 10, :i 7, :sum 21}
;    {:n 10, :i 8, :sum 28}
;    {:n 10, :i 9, :sum 36}
;    {:n 10, :i 10, :sum 45}
;    {:n 10, :i 11, :sum 55}]
```

### Integration with transducers

### Sessions

### Instrumentation

## Related works

- [scope-capture](https://github.com/vvvvalvalval/scope-capture)
- [miracle.save](https://github.com/Saikyun/miracle.save)

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
