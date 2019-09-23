(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core :as c]
            #?(:clj [net.cgrand.macrovich :as macros])
            [postmortem.protocols :as proto]
            [postmortem.session :as session])
  #?(:clj
     (:import [java.util.concurrent.locks ReentrantLock])
     :cljs
     (:require-macros [net.cgrand.macrovich :as macros]
                      [postmortem.core :refer [logpoint lp spy> spy>>]])))

(defn- valid-key? [x]
  (or (keyword? x) (symbol? x) (string? x) (integer? x)))

(defn session? [x]
  (satisfies? proto/ISession x))

(defn make-session
  ([] (make-session nil))
  ([name]
   (session/->ThreadUnsafeSession name {})))

#?(:clj
   (defn make-locking-session
     ([] (make-locking-session nil))
     ([name]
      (session/->LockingSession name (ReentrantLock.) (volatile! {})))))

(defn session-name [session]
  (proto/-name session))

(def ^:private ^:dynamic *current-session*
  (atom (make-session)))

(defn current-session []
  @*current-session*)

(defn set-current-session! [session]
  (c/reset! *current-session* session))

(macros/deftime

  (defmacro with-session [session & body]
    `(binding [postmortem.core/*current-session* (atom ~session)]
       ~@body))

  )

(defn log-for
  ([key] (log-for (current-session) key))
  ([session key]
   (assert (session? session) "Invalid session specified")
   (assert (valid-key? key) (str key " is not a valid key"))
   (get (proto/-logs session #{key}) key)))

(defn logs-for
  ([keys] (logs-for (current-session) keys))
  ([session keys]
   (assert (session? session) "Invalid session specified")
   (assert (coll? keys) "keys must be a collection")
   (proto/-logs session (set keys))))

(defn all-logs
  ([] (all-logs (current-session)))
  ([session]
   (assert (session? session) "Invalid session specified")
   (proto/-logs session)))

(defn reset!
  ([key-or-keys] (reset! (current-session) key-or-keys))
  ([session key-or-keys]
   (assert (session? session) "Invalid session specified")
   (assert (or (coll? key-or-keys) (valid-key? key-or-keys))
           "key-or-keys must be a valid key or a collection of valid keys")
   (let [keys (if (coll? key-or-keys) key-or-keys #{key-or-keys})]
     (proto/-reset! session (set keys))
     nil)))

(defn reset-all!
  ([] (reset-all! (current-session)))
  ([session]
   (assert (session? session) "Invalid session specified")
   (proto/-reset! session)
   nil))

(macros/deftime

  (defmacro logpoint
    ([key] `(logpoint ~key identity))
    ([key xform] `(logpoint (current-session) ~key ~xform))
    ([session key xform]
     (assert (valid-key? key) (str key " is not a valid key"))
     (let [vals (->> (macros/case :clj &env
                                  :cljs (:locals &env))
                     (into {} (map (fn [[k v]] `[~(keyword k) ~k]))))]
       `(do
          (proto/-add-item! ~session  '~key ~xform ~vals)
          nil))))

  (defmacro ^{:arglists '([key] [key xform] [session key xform])} lp [& args]
    `(logpoint ~@args))

  (defmacro spy>
    ([x key] `(spy> ~x ~key identity))
    ([x key xform] `(spy> ~x (current-session) ~key ~xform))
    ([x session key xform]
     (assert (valid-key? key) (str key " is not a valid key"))
     `(let [x# ~x]
        (proto/-add-item! ~session '~key ~xform x#)
        x#)))

  (defmacro spy>>
    ([key x] `(spy>> ~key identity ~x))
    ([key xform x] `(spy>> (current-session) ~key ~xform ~x))
    ([session key xform x] `(spy> ~x ~session ~key ~xform)))

  )
