(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core :as c]
            #?(:clj [net.cgrand.macrovich :as macros])
            [postmortem.protocols :as proto]
            [postmortem.session :as session])
  #?(:cljs
     (:require-macros [net.cgrand.macrovich :as macros]
                      [postmortem.core :refer [locals dump]])))

(defn session?
  "Returns true if x is a session."
  [x]
  (satisfies? proto/ISession x))

(defn make-unsafe-session
  "Creates and returns a new thread-unsafe session.
  Updates to a thread-unsafe session won't be synchronized among mulithreads.
  If all updates to a session need to be synchronized, use make-session instead.
  In ClojureScript, make-unsafe-session is exactly the same as make-session."
  ([] (make-unsafe-session identity))
  ([xform]
   (session/->ThreadUnsafeSession xform {})))

(defn make-session
  "Creates and returns a new session.
  Sessions created by this function are thread-safe and so all updates to them
  will be synchronized. Only if it is guaranteed that no more than one updates
  never happen simultaneously, make-unsafe-session can be used instead for better
  performance.
  In ClojureScript, make-session is exactly the same as make-unsafe-session."
  ([] (make-session identity))
  ([xform]
   #?(:clj (session/synchronized (make-unsafe-session xform))
      :cljs (make-unsafe-session xform))))

(def
  ^{:arglists '([])
    :doc "Returns a void session, which logs nothing and never triggers a call
  to transducer. It's useful to disable logging entirely."}
  void-session
  (let [session (session/void-session)]
    (fn [] session)))

(def ^:dynamic *current-session*
  "Dynamic var bound to the current session. Don't use this directly, call
  (current-session) instead."
  (atom (make-session)))

(defn current-session
  "Returns the current session."
  []
  @*current-session*)

(defn set-current-session!
  "Sets the current session to the specified one."
  [session]
  (c/reset! *current-session* session)
  nil)

(macros/deftime

  (defmacro with-session
    "Dynamically binds the current session to the specified one within the body
  of this form."
    [session & body]
    `(binding [*current-session* (atom ~session)]
       ~@body))

  )

(defn completed?
  "Returns true if the log entry for the specified key has been completed.
  If session is omitted, the log entry in the current session will be checked."
  ([key] (completed? (current-session) key))
  ([session key]
   (proto/-completed? session key)))

(defn- logs*
  ([session]
   (proto/-complete! session)
   (proto/-logs session))
  ([session keys]
   (proto/-complete! session keys)
   (proto/-logs session keys)))

(defn log-for
  "Completes log entry for the specified key and returns a vector of logged
  items in the entry.
  If session is omitted, the log will be pulled from the current session."
  ([key] (log-for (current-session) key))
  ([session key]
   (assert (session? session) "Invalid session specified")
   (get (logs* session #{key}) key)))

(defn- last-item [entry]
  (some->> entry rseq first))

(defn last-log-for
  "Completes log entry for the specified key and returns the last item in
  the entry.
  If session is omitted, the log will be pulled from the current session."
  ([key] (last-log-for (current-session) key))
  ([session key]
   (last-item (log-for session key))))

(defn logs-for
  "Completes log entries for the specified keys and returns a map of key to
  vector of logged items.
  If session is omitted, the logs will be pulled from the current session."
  ([keys] (logs-for (current-session) keys))
  ([session keys]
   (assert (session? session) "Invalid session specified")
   (assert (coll? keys) "keys must be a collection")
   (logs* session (set keys))))

(defn logs
  "Completes all log entries and returns a map of key to vector of logged items.
  If session is omitted, the logs will be pulled from the current session."
  ([] (logs (current-session)))
  ([session]
   (assert (session? session) "Invalid session specified")
   (logs* session)))

(defn reset-for!
  "Resets log entries for the specified key(s).
  If session is omitted, the entries in the current session will be reset."
  ([key-or-keys] (reset-for! (current-session) key-or-keys))
  ([session key-or-keys]
   (assert (session? session) "Invalid session specified")
   (let [keys (if (coll? key-or-keys) key-or-keys #{key-or-keys})]
     (proto/-reset! session (set keys))
     nil)))

(defn reset!
  "Resets all the log entries.
  If session is omitted, the entries in the current session will be reset."
  ([] (reset! (current-session)))
  ([session]
   (assert (session? session) "Invalid session specified")
   (proto/-reset! session)
   nil))

(defn spy>
  "Saves a value to the log entry corresponding to the specified key and returns
  the value as-is.
  If a transducer xform is specified, it will be applied when adding
  the value to the log entry. Defaults to clojure.core/identity.
  If session is specified, the value will be added to the log entry in that
  session. Otherwise, the value will be added to the log entry in the current
  session.
  spy> is intended to be used in combination with thread-first macros.
  In thread-last contexts, use spy>> instead."
  ([x key] (spy> x key identity))
  ([x key xform] (spy> x (current-session) key xform))
  ([x session key xform]
   (proto/-add-item! session key xform x)
   x))

(defn spy>>
  "A version of spy> intended to be used in combination with thread-last macros.
  See the docstring of spy> for more details."
  ([key x] (spy>> key identity x))
  ([key xform x] (spy>> (current-session) key xform x))
  ([session key xform x] (spy> x session key xform)))

(macros/deftime

  (defmacro locals
    "Creates and returns a local environment map at the call site.
  A local environment map is a map of keyword representing each local name
  in the scope at that position, to the value that the local name is bound to."
    [& names]
    (->> (cond-> (macros/case :clj &env :cljs (:locals &env))
           (seq names)
           (select-keys (map (comp symbol name) names)))
         (into {} (map (fn [[k _]] `[~(keyword k) ~k])))))

  (defmacro dump
    "Saves a local environment map to the log entry corresponding to the specified
  key.
  If a transducer xform is specified, it will be applied when adding
  the environment map to the log entry. Defaults to clojure.core/identity.
  If session is specified, the environment map will be added to the log entry in
  that session. Otherwise, the environment map will be added to the log entry in
  the current session."
    ([key] `(dump ~key identity))
    ([key xform] `(dump (current-session) ~key ~xform))
    ([session key xform]
     `(spy> (locals) ~session ~key ~xform)))

  )
