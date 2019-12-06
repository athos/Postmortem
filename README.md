# Postmortem
[![Clojars Project](https://img.shields.io/clojars/v/postmortem.svg)](https://clojars.org/postmortem)
[![CircleCI](https://circleci.com/gh/athos/postmortem.svg?style=shield)](https://circleci.com/gh/athos/postmortem)
[![codecov](https://codecov.io/gh/athos/postmortem/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/postmortem)

A tiny value-oriented debugging tool for Clojure(Script), powered by transducers

## Features

- Postmortem encourages data-driven approaches in the debugging process
  - Logs are just Clojure data, so you can use DataScript, REBL or whatever tools for more sophisticated log analysis
- [Integration with transducers](#integration-with-transducers) enables various flexible logging strategies
- [Instrumentation](#instrumentation) on vars makes it easier to debug functions without touching their code
- Supports Clojure/ClojureScript/self-hosted ClojureScript
- Possible to use for debugging multi-threaded programs

## Synopsis

```clojure
(require '[postmortem.core :as pm]
         '[postmortem.xforms :as xf])

(defn sum [n]
  (loop [i n sum 0]
    (pm/dump :sum (comp (filter (fn [{:keys [i]}] (even? i)))
                        (xf/take-last 5)))
    (if (= i 0)
      sum
      (recur (dec i) (+ i sum)))))

(sum 100) ;=> 5050
(pm/log-for :sum)
;=> [{:n 100, :i 8, :sum 5014}
;    {:n 100, :i 6, :sum 5029}
;    {:n 100, :i 4, :sum 5040}
;    {:n 100, :i 2, :sum 5047}
;    {:n 100, :i 0, :sum 5050}]


(require '[postmortem.instrument :as pi])

(defn broken-factorial [n]
  (cond (= n 0) 1
        (= n 7) (* (broken-factorial (dec n))
                   (throw (ex-info "Something bad has happened!!" {}))) ;; <- bug here
        :else (* n (broken-factorial (dec n)))))

(pi/instrument `broken-factorial
               {:xform (comp (xf/take-until :err) (xf/take-last 5))})

(broken-factorial 10)
;; Execution error (ExceptionInfo) at user/broken-factorial.
;; Something bad has happened!!

(pm/log-for `broken-factorial)
;=> [{:args (3), :ret 6}
;    {:args (4), :ret 24}
;    {:args (5), :ret 120}
;    {:args (6), :ret 720}
;    {:args (7), :err #error {:cause "Something bad has happened!!" ...}}]
```

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
  - [Basic usage](#basic-usage)
    - [`spy>>` / `log-for`](#spy--log-for)
    - [`reset-key!` / `completed?`](#reset-key--completed)
    - [`logs` / `reset!`](#logs--reset)
    - [`spy>`](#spy)
    - [`dump`](#dump)
  - [Integration with transducers](#integration-with-transducers)
  - [Sessions](#sessions)
    - [Handling sessions](#handling-sessions)
    - [Attaching a transducer](#attaching-a-transducer)
    - [`void-session`](#void-session)
    - [`make-unsafe-session`](#make-unsafe-session)
  - [Instrumentation](#instrumentation)
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

#### `reset-key!` / `completed?`

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
clearing each individual log entry one by one calling `reset-key!`:

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

is equavalent to:

```clojure
(pm/set-current-sesion! (pm/make-session))

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
(pm/with-sesion (pm/void-session)
  (pm/spy>> :foo 2)
  (pm/spy>> :foo 3))
(pm/spy>> :foo 4)

(pm/log-for :foo)
;=> [1 4]
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

Under the hood, instrumenting a function `f` replaces `f` with something like this:

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
        (= n 5) (* (broken-factorial (dec n))
                   (throw (ex-info "Something bad has happened!!" {})))
        :else (* n (broken-factorial (dec n)))))

;; So far so good
(map broken-factorial (range 5))
;=> (1 1 2 6 24)

;; Boom!!
(broken-factorial 7)
;; Execution error (ExceptionInfo) at user/broken-factorial
;; Something bad has happened!!

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
;    {:args (5), :err #error {:cause "Something bad has happened!!" ...}}
;    {:args (6), :err #error {:cause "Something bad has happened!!" ...}}
;    {:args (7), :err #error {:cause "Something bad has happened!!" ...}}]"
```

Moreover, transducer integration makes instrumentation way more powerful.
Specify the `{:xform <xform>}` option to attach a transducer `<xform>` when
instrumenting a fuction. The example below shows how you can utilize a transducer
to narrow down the execution log to the last few items until immediately before 
an error occurs:

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
;    {:args (5), :err #error {:cause "Something bad has happened!!" ...}}]
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
