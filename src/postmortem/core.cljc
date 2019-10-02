(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core :as c]
            #?(:clj [net.cgrand.macrovich :as macros])
            [postmortem.protocols :as proto]
            [postmortem.session :as session])
  #?(:cljs
     (:require-macros [net.cgrand.macrovich :as macros]
                      [postmortem.core :refer [save]])))

(defn session?
  "Returns true if x is a session."
  [x]
  (satisfies? proto/ISession x))

(defn make-session
  "Creates and returns a new session.
  Note that sessions created by this function are thread-unsafe.
  If updates to a session need to be synchronized among multithreads,
  use make-synchronized-session instead."
  ([] (make-session identity))
  ([xform]
   (session/->ThreadUnsafeSession xform {})))

#?(:clj
   (defn make-synchronized-session
     "Creates and returns a new synchronized session.
  A synchronized session is almost same as an ordinary session except that all
  updates to a synchronized session will be synchronized."
     ([] (make-synchronized-session identity))
     ([xform]
      (session/synchronized (make-session xform)))))

(def ^:private ^:dynamic *current-session*
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

  (defmacro save
    "Saves a local environment map to the log entry corresponding to the specified
  key. A local environment map is a map of keyword representing each local name
  in the scope at that position, to the value that the local name is bound to.
  If a transducer xform is specified, it will be applied when adding
  the environment map to the log entry. Defaults to clojure.core/identity.
  If session is specified, the environment map will be added to the log entry in
  that session. Otherwise, the environment map will be added to the log entry in
  the current session."
    ([key] `(save ~key identity))
    ([key xform] `(save (current-session) ~key ~xform))
    ([session key xform]
     (let [vals (->> (macros/case :clj &env
                                  :cljs (:locals &env))
                     (into {} (map (fn [[k v]] `[~(keyword k) ~k]))))]
       `(do
          (proto/-add-item! ~session ~key ~xform ~vals)
          nil))))

  )
