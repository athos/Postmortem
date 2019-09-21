(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core :as c]
            #?(:clj [net.cgrand.macrovich :as macros])
            [postmortem.protocols :as proto]
            [postmortem.session :as session])
  #?(:cljs
     (:require-macros [net.cgrand.macrovich :as macros]
                      [postmortem.core :refer [logpoint lp spy> spy>>]])))

(defn make-session
  ([] (make-session nil))
  ([name]
   (session/->Session name (atom {}))))

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
   (get (proto/-logs session #{key}) key)))

(defn logs-for
  ([keys] (logs-for (current-session) keys))
  ([session keys]
   (proto/-logs session (set keys))))

(defn all-logs
  ([] (all-logs (current-session)))
  ([session]
   (proto/-logs session)))

(defn reset!
  ([key-or-keys] (reset! (current-session) key-or-keys))
  ([session key-or-keys]
   (let [keys (if (coll? key-or-keys) key-or-keys #{key-or-keys})]
     (proto/-reset! session (set keys))
     nil)))

(defn reset-all!
  ([] (reset-all! (current-session)))
  ([session]
   (proto/-reset! session)
   nil))

(macros/deftime

  (defmacro logpoint
    ([key] `(logpoint ~key identity))
    ([key xform] `(logpoint (current-session) ~key ~xform))
    ([session key xform]
     (let [vals (->> (macros/case :clj &env
                                  :cljs (:locals &env))
                     (into {} (map (fn [[k v]] `[~(keyword k) ~k]))))]
       `(proto/-add-item! ~session  ~key ~xform ~vals))))

  (defmacro ^{:arglists '([key] [key xform] [session key xform])} lp [& args]
    `(logpoint ~@args))

  (defmacro spy>
    ([x key] `(spy> ~x ~key identity))
    ([x key xform] `(spy> ~x (current-session) ~key ~xform))
    ([x session key xform]
     `(let [x# ~x]
        (proto/-add-item! ~session ~key ~xform x#)
        x#)))

  (defmacro spy>>
    ([key x] `(spy>> ~key identity ~x))
    ([key xform x] `(spy>> (current-session) ~key ~xform ~x))
    ([session key xform x] `(spy> ~x ~session ~key ~xform)))

  )
