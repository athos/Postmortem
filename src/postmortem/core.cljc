(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [postmortem.protocols :as proto]
            [postmortem.session :as session]))

(defn make-session
  ([] (make-session nil))
  ([name]
   (session/->Session name (atom {}))))

(defn session-name [session]
  (proto/-name session))

(def ^:private ^:dynamic *default-session* (make-session))

(defn default-session []
  *default-session*)

(defn set-default-session! [session]
  (alter-var-root #'*default-session* (constantly session)))

(defmacro with-session [session & body]
  `(binding [*default-session* ~session]
     ~@body))

(defn log-for
  ([id] (log-for (default-session) id))
  ([session id]
   (get (proto/-logs session #{id}) id)))

(defn logs-for
  ([ids] (logs (default-session) ids))
  ([session ids]
   (proto/-logs session (set ids))))

(defn all-logs
  ([] (all-logs (default-session)))
  ([session]
   (proto/-logs session)))

(defn reset!
  ([ids] (reset! (default-session) ids))
  ([session ids]
   (proto/-reset! session (set ids))
   nil))

(defn reset-all!
  ([] (reset-all! (default-session)))
  ([session]
   (proto/-reset! session)
   nil))

(defmacro logpoint
  ([id] `(logpoint ~id identity))
  ([id xform] `(logpoint (default-session) ~id ~xform))
  ([session id xform]
   (assert (keyword? id) "ID must be keyword")
   (let [vals (into {} (map (fn [[k v]] `[~(keyword k) ~k])) &env)]
     `(proto/-add-item! ~session  ~id ~xform ~vals))))

(defmacro ^{:arglists '([id] [id xform] [session id xform])} lp [& args]
  `(logpoint ~@args))

(defmacro spy>
  ([x id] `(spy> ~x ~id identity))
  ([x id xform] `(spy> ~x (default-session) ~id ~xform))
  ([x session id xform]
   (assert (keyword? id) "ID must be keyword")
   `(let [x# ~x]
      (proto/-add-item! ~session ~id ~xform x#)
      x#)))

(defmacro spy>>
  ([id x] `(spy>> ~id identity ~x))
  ([id xform x] `(spy>> (default-session) ~id ~xform ~x))
  ([session id xform x] `(spy> ~x ~session ~id ~xform)))
