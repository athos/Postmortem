# Postmortem
[![Clojars Project](https://img.shields.io/clojars/v/postmortem.svg)](https://clojars.org/postmortem)
![build](https://github.com/athos/Postmortem/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/postmortem/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/postmortem)

A tiny data-oriented debugging tool for Clojure(Script), powered by transducers

## Features

- Postmortem strongly encourages data-oriented debugging approaches
  - Logs are just Clojure data, so you can use DataScript, REBL or whatever tools for more sophisticated log analysis
- [Integration with transducers](#integration-with-transducers) enables various flexible logging strategies
- [Instrumentation](#instrumentation) on vars makes it easier to debug functions without touching their code
- Supports most of Clojure platforms (namely, Clojure, ClojureScript, self-hosted ClojureScript and Babashka)
- Possible to use for debugging multi-threaded programs

## Synopsis

```clojure
(require '[postmortem.core :as pm]
         '[postmortem.xforms :as xf])

(defn sum [n]
  (loop [i n sum 0]
    (pm/dump :sum (xf/take-last 5))
    (if (= i 0)
      sum
      (recur (dec i) (+ i sum)))))

(sum 100) ;=> 5050

(pm/log-for :sum)
;=> [{:n 100, :i 4, :sum 5040}
;    {:n 100, :i 3, :sum 5044}
;    {:n 100, :i 2, :sum 5047}
;    {:n 100, :i 1, :sum 5049}
;    {:n 100, :i 0, :sum 5050}]


(require '[postmortem.instrument :as pi])

(defn broken-factorial [n]
  (cond (= n 0) 1
        (= n 7) (/ (broken-factorial (dec n)) 0) ;; <- BUG HERE!!
        :else (* n (broken-factorial (dec n)))))

(pi/instrument `broken-factorial
               {:xform (comp (xf/take-until :err) (xf/take-last 5))})

(broken-factorial 10)
;; Execution error (ArithmeticException) at user/broken-factorial.
;; Divide by zero

(pm/log-for `broken-factorial)
;=> [{:args (3), :ret 6}
;    {:args (4), :ret 24}
;    {:args (5), :ret 120}
;    {:args (6), :ret 720}
;    {:args (7), :err #error {:cause "Divide by zero" ...}}]
```

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
  - [Basic usage](#basic-usage)
    - [`spy>>` / `log-for`](#spy--log-for)
    - [`reset-key!` / `completed?` / `keys`](#reset-key--completed--keys)
    - [`logs` / `stats` / `reset!`](#logs--stats--reset)
    - [`spy>`](#spy)
    - [`dump`](#dump)
  - [Integration with transducers](#integration-with-transducers)
  - [Sessions](#sessions)
    - [Handling sessions](#handling-sessions)
    - [Attaching a transducer](#attaching-a-transducer)
    - [`void-session`](#void-session)
    - [Indexed sessions](#indexed-sessions)
    - [`make-unsafe-session`](#make-unsafe-session)
  - [Simple logger](#simple-logger)
  - [Instrumentation](#instrumentation)
- [Related works](#related-works)

## Requirements

- Clojure 1.8+, or
- ClojureScript 1.10.238+, or
- Babashka v0.10.163+, or
- Planck 2.24.0+, or
- Lumo 1.10.1+

We have only tested on the above environments, but you could possibly use
the library on some older versions of the ClojureScript runtimes as well.

## Installation

Add the following to your project dev dependencies or the `:user` profile in `~/.lein/profiles.clj`:

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
`[0 1 3 6 10 15]`, which corresponds to the intermediate summations from 0 to 5:

```clojure
(sum 5)
;=> 15

(pm/log-for :sum)
;=> [0 1 3 6 10 15]
```

Any Clojure data can be used as a log entry key, such as keywords (as in the above examples),
symbols, integers, strings or whatever.
In fact, you can even use a runtime value as a key, as well as literal values, and
thus entry keys can also be used as a handy way to collect and group log data:

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

#### `reset-key!` / `completed?` / `keys`

To clear the logged data at the log entry `<key>`, call `(reset-key! <key>)`:

```clojure
(pm/log-for :sum)
;=> [0 1 3 6 10 15]

(pm/reset-key! :sum)

(pm/log-for :sum)
;=> nil
```

Note that once you call `log-for` for a key `k`, the log entry for `k` will be *completed*.
A completed log entry will not be changed anymore until you call `reset-key!` for the log entry `k`:

```clojure
(pm/spy>> :foobar 1)
(pm/spy>> :foobar 2)
(pm/log-for :foobar)
;=> [1 2]

(pm/spy>> :foobar 3)
(pm/spy>> :foobar 4)
(pm/log-for :foobar)
;=> [1 2]

(pm/reset-key! :foobar)

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

(pm/reset-key! :barbaz)
(pm/completed? :barbaz)
;=> false
```

If you want to know what log entry keys have been logged so far without completing
any log entry, `keys` suits your desire:

```clojure
(pm/spy>> :bazqux 10)
(pm/spy>> :quxquux 20)

(pm/keys)
;=> #{:bazqux :quxquux}
(pm/completed? :bazqux)
;=> false
(pm/completed? :quxquux)
;=> false
```

#### `logs` / `stats` / `reset!`

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

Alternatively, `stats` helps you grasp how many log items have been
stored so far for each log entry key, without seeing the actual log data:

```clojure
(defn sum [n]
  (loop [i 0 sum 0]
    (pm/spy>> :i i)
    (if (> i n)
      sum
      (recur (inc i)
             (pm/spy>> :sum (+ i sum))))))

(sum 5) ;=> 15

(pm/stats)
;=> {:i 7 :sum 6}

;; As compared to:
;; (pm/logs)
;; => {:i [0 1 2 3 4 5 6],
;      :sum [0 1 3 6 10 15]}
```

Note that once you call `stats`, all the log entries will be
*completed*, as with the `logs` fn.

For those who are using older versions (<= 0.4.0), `pm/stats` is the new name
for `pm/frequencies` added in 0.4.1. They can be used totally interchangeably.
Now `pm/stats` is recommended for primary use.

Analogous to `logs`, `reset!` is useful to clear the whole log data at a time,
rather than clearing each individual log entry one by one calling `reset-key!`:

```clojure
(pm/logs)
;=> {:i [0 1 2 3 4 5 6],
;    :sum [0 1 3 6 10 15]}

(pm/reset!)

(pm/logs)
;=> {}
```

#### `spy>`

`spy>>` has a look-alike cousin named `spy>`. They have no semantic difference,
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

`(dump <key>)` stores a local environment map to the log entry `<key>`.
A *local environment map* is a map of keywords representing local names in the scope
at the callsite, to the values that the corresponding local names are bound to.

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
What if you only need the last few log items out of them?

That's where Postmortem really shines. It achieves extremely flexible customizability of logging strategies
by integration with transducers (If you are not familiar with transducers, we recommend that you take
a look at the [official reference](https://clojure.org/reference/transducers) first).

Postmortem's logging operators (`spy>>`, `spy>` and `dump`) are optionally takes a transducer
after the log entry key. When you call `(spy>> <key> <xform> <expr>)`, the transducer `<xform>`
controls whether or not the given data will be logged and/or what data will actually be stored.

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

You see two invocations to `spy>>` in the example code. The first one is an ordinary
invocation without a transducer. The second one is called with a transducer `(filter odd?)`.
With that transducer, the log entry for the key `:sum2` only stores odd numbers
while the entry for `:sum1` holds every intermediate sum result.

Not only `filter`, you can use any transducer to customize how a log item will be stored.
The following code uses a transducer `(map (fn [m] (select-keys m [:sum])))`, which
only stores the value of the local binding `sum` for each iteration:

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

Also, transducers can be used to restrict the maximum log size.
For example, `(take <n>)` only allows the first up to `<n>` items to be logged:

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

`(drop-while <pred>)` would drop log data until `<pred>` returns `false`:

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

You can even pick up logs by random sampling using `(random-sample <prob>)`:

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

Postmortem also has its own set of utility transducers. The namespace
`postmortem.xforms` provides several useful transducers to implement
specific logging strategies.

`take-last` is one of the most useful transducer of those.
`(take-last <n>)` just logs the last `<n>` items, which only
requires a fixed-size buffer (rather than an indefinite-size one)
to store the desired range of logs even when the logging operator is
repeatedly invoked millions of times:

```clojure
(require '[postmortem.xforms :as xf])

(defn sum [n]
  (loop [i 0 sum 0]
    (pm/dump :sum (xf/take-last 5))
    (if (> i n)
      sum
      (recur (inc i) (+ i sum)))))

(sum 1000000) ;=> 500000500000

(pm/log-for :sum)
;=> [{:n 1000000, :i 999997, :sum 499996500006}
;    {:n 1000000, :i 999998, :sum 499997500003}
;    {:n 1000000, :i 999999, :sum 499998500001}
;    {:n 1000000, :i 1000000, :sum 499999500000}
;    {:n 1000000, :i 1000001, :sum 500000500000}]
```

### Sessions

A session is an abstraction responsible for what actually happens when
storing and retrieving logs and where the actual log data will be stored.
It can be used to completely isolate some logs from the other, or to
enable/disable the entire logging mechanism, etc.

#### Handling sessions

Postmortem's logging operators takes another optional argument for session.
For example, `(spy>> <session> <key> <expr> <xforms>)` stores logs
into the `<session>`.

To make a new session, use `make-session`:

```clojure
(def sess (pm/make-session))

(pm/spy>> sess :foo 1 identity)
(pm/spy>> sess :bar 2 identity)
(pm/spy>> sess :foo 3 identity)
```

Note that to specify your session explicitly, you'll need to specify a transducer
even if you don't really want to change a logging strategy using it.
Here, the `identity` transducer is specified as a placeholder.

To retrieve log data from the session `sess`, call `log-for` / `logs` with it:

```clojure
(pm/log-for sess :foo)
;=> [1 3]
(pm/logs sess)
;=> {:foo [1 3) :bar [2]]}
```

When you omit a session, things behave as if the current session were specified.
The *current session* is the default session that can be accessed globally.

To get the current session, use `current-session`:

```clojure
(pm/current-session)
;; Returns the current session object
```

And the following pairs of expressions are semantically identical, respectively:

```clojure
(pm/spy>> :foo identity {:foo 42})
(pm/spy>> (pm/current-session) :foo identity {:foo 42})

(pm/dump :foo identity)
(pm/dump (pm/current-session) :foo identity)

(pm/log-for :foo)
(pm/log-for (pm/current-session) :foo)

(pm/logs)
(pm/logs (pm/current-session))
```

To set the current session to your own session, you can use `set-current-session!`:

```clojure
(def sess (pm/make-session))
(pm/set-current-session! sess)

(pm/spy>> :foo 1)
(pm/spy>> :foo 2)
(pm/spy>> :foo 3)

(pm/log-for :foo)
;=> [1 2 3]
(pm/log-for sess :foo)
;=> [1 2 3]
```

Or you can temporarily change the current session with `with-session`:

```clojure
(def sess (pm/make-session))

(pm/spy>> :foo 1)
(pm/with-session sess
  (pm/spy>> :foo 2)
  (pm/spy>> :foo 3))
(pm/spy>> :foo 4)

(pm/log-for :foo)
;=> [1 4]
(pm/log-for sess :foo)
;=> [2 3]
```

#### Attaching a transducer

Transducers can be attached to sessions as well. Those transducers attached to a session
are called a *base transducer* of the session. To make a session with a base transducer
attached, call `(make-session <xform>)`.
If a session has a base transducer, a logging operator operating on the session will behave
as if it were called (1) with that base transducer, or (2) with a transducer produced
by prepending (a la `comp`) the base transducer to the transducer that is originally 
passed to the logging operator, if any.

For example for case 1, this code

```clojure
(pm/set-current-session! (pm/make-session (take 5)))

(pm/dump :key)
```

is equivalent to the following:

```clojure
(pm/set-current-session! (pm/make-session))

(pm/dump :key (take 5))
```

And for case 2, this

```clojure
(pm/set-current-session! (pm/make-session (drop 5)))

(pm/dump :key (take 5))
```

is equivalent to:

```clojure
(pm/set-current-session! (pm/make-session))

(pm/dump :key (comp (drop 5) (take 5)))
```

This feature is useful to apply a common transducer to all the logging operators
operating on the same session.

#### `void-session`

A void session is another implementation of Postmortem session which does nothing
at all. It's useful to disable the logging operators operating on the current session.

To get the void session, call `void-session`:

```clojure
(pm/set-current-session! (pm/void-session))

(pm/spy>> :foo 1)
(pm/spy>> :foo 2)
(pm/spy>> :foo 3)

(pm/log-for :foo)
;=> []
```

Using it together with `with-session` disables logging temporarily:

```clojure
(pm/set-current-session! (pm/make-session))

(pm/spy>> :foo 1)
(pm/with-session (pm/void-session)
  (pm/spy>> :foo 2)
  (pm/spy>> :foo 3))
(pm/spy>> :foo 4)

(pm/log-for :foo)
;=> [1 4]
```

#### Indexed sessions

When dealing with multiple log entries, it is sometimes useful to have a sequential
number (or index) for each log item throughout all the entries.

An *indexed session* automatically adds an auto-increment index to each log item.
To create a new indexed session, use `make-indexed-session`:

```clojure
(pm/set-current-session! (pm/make-indexed-session))

(pm/spy>> :foo 100)
(pm/spy>> :bar 101)
(pm/spy>> :foo 102)

(pm/logs)
;=> {:foo [{:id 0 :val 100}
;          {:id 2 :val 102}]
;    :bar [{:id 1 :val 101}]}
```

Calling `reset!` on the indexed session resets the index:

```clojure
(pm/spy>> :foo 100)
(pm/spy>> :foo 101)
(pm/log-for :foo)
;=> [{:id 0 :val 100} {:id 1 :val 101}]

(pm/reset!)

(pm/spy>> :foo 102)
(pm/spy>> :foo 103)
(pm/log-for :foo)
;=> [{:id 0 :val 102} {:id 1 :val 103}]
```

`make-indexed-session` takes an optional function to specify how the indexed
session will attach the index to each log item.

The function must take two arguments, the index and the log item, and return
a new log item. The default function is `(fn [id item] {:id id :val item})`.

The example below shows how it takes effect:

```clojure
(pm/set-current-session!
  (pm/make-indexed-session (fn [id item] [id item])))

(pm/spy>> :foo :a)
(pm/spy>> :foo :b)
(pm/spy>> :foo :c)
(pm/log-for :foo)
;=> [[0 :a] [1 :b] [2 :c]]

(pm/set-current-session!
  (pm/make-indexed-session #(assoc %2 :i %1)))

(pm/spy>> :point {:x 100 :y 100})
(pm/spy>> :point {:x 200 :y 200})
(pm/spy>> :point {:x 300 :y 300})
(pm/log-for :point)
;=> [{:i 0 :x 100 :y 100}
;    {:i 1 :x 200 :y 200}
;    {:i 2 :x 300 :y 300}]
```

The `indexed` function is another way to create an indexed session.
`(indexed <session>)` creates a new indexed session based on another session.
In fact, `(make-indexed-session)` is equivalent to `(indexed (make-session))`.

It's especially useful to make an session with base transducer into an indexed session:

```clojure
(pm/set-current-session!
  (pm/indexed (pm/make-session (take-while #(< (:id %) 3)))))

(doseq [v [:a :b :c :d :e]]
  (pm/spy>> :foo v))
(pm/log-for :foo)
;=> [{:id 0 :val :a}
;    {:id 1 :val :b}
;    {:id 2 :val :c}]
```

#### `make-unsafe-session`

In Clojure, an ordinary session (created by `make-session`) is inherently
thread safe, so you can safely share and update it across more than
one threads:

```clojure
(def sess (pm/make-session))
(pm/set-current-session! sess)

(defn f [n]
  (pm/dump :f))

(run! deref [(future (dotimes [i 10000] (f i)))
             (future (dotimes [i 10000] (f i)))])

(count (pm/log-for sess :f)) ;=> 20000
```

This thread safety is achieved by means of pessimistic locking during
the session update. If it is guaranteed that no more than one updates
never happen simultaneously on a single session, you can use
`make-unsafe-session` instead to avoid the overhead of mutual exclusion.
`make-unsafe-session` behaves almost same as `make-session` except for
thread safety and performance.

```clojure
;; you can use `make-unsafe-session` in the same way as `make-session`
(def sess (pm/make-unsafe-session))
(pm/set-current-session! sess)

(pm/spy>> :foo 1)
(pm/spy>> :foo 2)
(pm/spy>> :foo 3)

(pm/log-for sess :foo) ;=> [1 2 3]

;; but `make-unsafe-session` is not thread safe
(defn f [n]
  (pm/dump :f))

(run! deref [(future (dotimes [i 10000] (f i)))
             (future (dotimes [i 10000] (f i)))])

(count (pm/log-for sess :f)) ;=> 11055
```

In ClojureScript, `make-session` is completely identical to `make-unsafe-session`.

### Simple logger

Postmortem 0.5.0+ provides a new feature, *simple loggers*. A simple logger
essentially works like `spy>>` and `log-for`, but offers a more simplified API
for common use cases.

A simple logger is implemented as a function with two arities that closes over
an implicit session:

```clojure
(def f (pm/make-logger))

(f 1)
(f 2)
(f 3)
(f 4)
(f) ;=> [1 2 3 4]
```

As you may see, if a simple logger is called with one argument, it acts like
`(spy>> :key <argument>)` on the implicit session whereas if called with no
argument, it acts like `(log-for :key)`.

If you create a simple logger by passing an transducer as the optional argument,
it will behave as if you attached that transducer to the implicit session:

```clojure
(def f (pm/make-logger (map #(* % %))))

(f 1)
(f 2)
(f 3)
(f 4)
(f) ;=> [1 4 9 16]
```

A *multi logger* is a variant of the simple logger that has three arities, i.e.
2-arg for `(spy>> <arg1> <arg2>)`, 1-arg for `(log-for <arg>)` and 0-arg for `(logs)`.

```clojure
(def f (pm/make-multi-logger))

(loop [n 5, sum 0]
  (if (= n 0)
    sum
    (recur (f :n (dec n)) (f :sum (+ sum n)))))
;=> 15

(f) ;=> {:n [4 3 2 1 0], :sum [5 9 12 14 15]}
(f :n) ;=> [4 3 2 1 0]
(f :sum) ;=> [5 9 12 14 15]
```

### Instrumentation

Postmortem has one more powerful feature: instrumentation. It looks like clojure.spec's
feature with the same name. Once you instrument a function, you can collect execution log
for it without touching its code.

To use the instrumentation feature, you'll need to require the `postmortem.instrument`
namespace. The namespace provides two macros, `instrument` and `unstrument`, which
enables/disables logging for a specified var, respectively.

```clojure
(require '[postmortem.core :as pm]
         '[postmortem.instrument :as pi])

(defn f [x] (inc x))

;; Instruments `f` to enable logging
(pi/instrument `f)
;=> [user/f]

(dotimes [i 5] (prn (f i)))

(pm/log-for `f)
;=> [{:args (0)}
;    {:args (0), :ret 1}
;    {:args (1)}
;    {:args (1), :ret 2}
;    {:args (2)}
;    {:args (2), :ret 3}
;    {:args (3)}
;    {:args (3), :ret 4}
;    {:args (4)}
;    {:args (4), :ret 5}]

;; Unstruments `f` to disable logging
(pi/unstrument `f)
;=> [user/f]

(pm/reset!)

(dotimes [i 5] (prn (f i)))
(pm/log-for `f) ;=> nil
```

As you can see, the execution log for an instrumented function consists of two types
of log items, ones for *entry* and ones for *exit*. An entry log item is logged
immediately after the function gets called whereas an exit log item is logged
immediately after the function either returns or throws.

Both types of the log items contain the `:args` key that represents the arguments
passed to the function when it's called. An exit log item contains an extra key:
If the function exits normally, the corresponding exit log item will have the `:ret` key
that holds the return value, and otherwise (i.e. if the function fails) the exit log
item will have the `:err` key that holds the error object thrown in the function.

Virtually, instrumenting a function `f` replaces `f` with something like this:

```clojure
(fn [& args]
  (pm/spy>> `f {:args args})
  (try
    (let [ret (apply f args)]
      (pm/spy>> `f {:args args :ret ret}))
    (catch Throwable t
      (pm/spy>> `f {:args args :err t}))))
```

This error handling behavior may be useful to identify which function call
actually caused an error especially when you are debugging a recursive function:

```clojure
(defn broken-factorial [n]
  (cond (= n 0) 1
        (= n 5) (/ (broken-factorial (dec n)) 0) ;; <- BUG HERE!!
        :else (* n (broken-factorial (dec n)))))

;; So far so good
(map broken-factorial (range 5))
;=> (1 1 2 6 24)

;; Boom!!
(broken-factorial 7)
;; Execution error (ArithmeticException) at user/broken-factorial.
;; Divide by zero

;; Now let's look into it further!
(pi/instrument `broken-factorial)
;=> [user/broken-factorial]

(broken-factorial 7) ;; throws error
(pm/log-for `broken-factorial)
;=> [{:args (7)}
;    {:args (6)}
;    {:args (5)}
;    {:args (4)}
;    {:args (3)}
;    {:args (2)}
;    {:args (1)}
;    {:args (0)}
;    {:args (0), :ret 1}
;    {:args (1), :ret 1}
;    {:args (2), :ret 2}
;    {:args (3), :ret 6}
;    {:args (4), :ret 24}
;    {:args (5), :err #error {:cause "Divide by zero" ...}}
;    {:args (6), :err #error {:cause "Divide by zero" ...}}
;    {:args (7), :err #error {:cause "Divide by zero" ...}}]"
```

There are a couple of options to specify for the `instrument` macro:

- `:with-depth <bool>`: Attaches `:depth` to each execution log
- `:xform <xform>`: Enables transducer integration
- `:session <session>`: Specifies the session to use

If you call `instrument` with the option `{:with-depth true}`,
Postmortem automatically counts the current nesting level (depth) of
function calls and attach it to each execution log:

```clojure
(defn fact [n]
  (if (= n 0)
    1
    (* n (fact (dec n)))))

(pi/instrument `fact {:with-depth true})

(fact 5) ;=> 120
(pm/log-for `fact)
;=> [{:depth 1, :args (5)}
;    {:depth 2, :args (4)}
;    {:depth 3, :args (3)}
;    {:depth 4, :args (2)}
;    {:depth 5, :args (1)}
;    {:depth 6, :args (0)}
;    {:depth 6, :args (0), :ret 1}
;    {:depth 5, :args (1), :ret 1}
;    {:depth 4, :args (2), :ret 2}
;    {:depth 3, :args (3), :ret 6}
;    {:depth 2, :args (4), :ret 24}
;    {:depth 1, :args (5), :ret 120}]
```

Calling `instrument` with the option `{:xform <xform>}` enables integration
of transducers with the instrumentation facility, passing the transducer
`<xform>` to the underlying logging operators behind instrumentation.
The example below shows how you can utilize a transducer to narrow down
the execution log to the last few items until immediately before an error occurs:

```clojure
(require '[postmortem.core :as pm]
         '[postmortem.instrument :as pi]
         '[postmortem.xforms :as xf])

(pm/reset!)

(pi/instrument `broken-factorial
               {:xform (comp (xf/take-until :err) (xf/take-last 5))})
;=> [user/broken-factorial]

(broken-factorial 7) ;; throws error
(pm/log-for `broken-factorial)
;=> [{:args (1), :ret 1}
;    {:args (2), :ret 2}
;    {:args (3), :ret 6}
;    {:args (4), :ret 24}
;    {:args (5), :err #error {:cause "Divide by zero" ...}}]
```

Also, you can even isolate the session for a function instrumentation
by specifying the `{:session <session>}` option:

```clojure
(def sess (pm/make-session))

(defn f [x] (inc x))

(pi/instrument `f {:session sess})

(dotimes [i 5] (prn (f i)))

(pm/log-for `f) ;=> nil
(pm/log-for sess `f)
;=> [{:args (0)}
;    {:args (0), :ret 1}
;    {:args (1)}
;    {:args (1), :ret 2}
;    {:args (2)}
;    {:args (2), :ret 3}
;    {:args (3)}
;    {:args (3), :ret 4}
;    {:args (4)}
;    {:args (4), :ret 5}]
```

## Related works

- [scope-capture](https://github.com/vvvvalvalval/scope-capture): Focuses on recreating a local environment at some point and seems rather intended to be a simple alternative for rich debuggers while Postmortem aims to compensate for some weakness in existing debuggers
- [miracle.save](https://github.com/Saikyun/miracle.save): Closely resembles Postmortem's basics, but Postmortem has more fancy features such as transducer integration and instrumentation

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
