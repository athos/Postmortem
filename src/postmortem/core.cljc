(ns postmortem.core
  (:refer-clojure :exclude [reset!])
  (:require [clojure.core :as c]))

(def ^:private logs* (atom {}))

(defn- complete-log-entry [entry]
  (if (:completed? entry)
    entry
    (-> entry
        (update :items (:fn entry))
        (assoc :completed? true))))

(defn- complete-logs [logs]
  (reduce-kv (fn [m id entry]
               (let [entry' (complete-log-entry entry)]
                 (if (identical? entry entry')
                   m
                   (assoc m id entry'))))
             logs
             logs))

(defn logs
  ([]
   (swap! logs* complete-logs)
   (reduce-kv (fn [m k v] (assoc m k (:items v))) {} @logs*))
  ([id]
   (swap! logs* update id complete-log-entry)
   (:items (get @logs* id))))

(defn reset!
  ([& ids]
   (if (empty? ids)
     (c/reset! logs* {})
     (apply swap! logs* dissoc ids))
   nil))

(defn- xf->rf [xform]
  (let [rf (xform conj)
        finished? (volatile! false)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([items item]
       (if @finished?
         items
         (let [ret (rf items item)]
           (when (reduced? ret)
             (vreset! finished? true))
           (unreduced ret)))))))

(defn enqueue! [id xform item]
  (swap! logs* update id
         (fn [entry]
           (as-> entry e
             (if (nil? e)
               (let [rf (xf->rf xform)]
                 (assoc e :fn rf :completed? false :items (rf)))
               e)
             (update e :items (:fn e) item)
             (if (identical? (:items entry) (:items e))
               e
               (assoc e :completed? false))))))

(defmacro logpoint
  ([id]
   (with-meta `(logpoint ~id identity) (meta &form)))
  ([id xform]
   (assert (keyword? id) "ID must be keyword")
   (let [vals (into {} (map (fn [[k v]] `[~(keyword k) ~k])) &env)]
     `(enqueue! ~id ~xform ~vals))))

(defmacro lp
  ([id] (with-meta `(logpoint ~id) (meta &form)))
  ([id xform] (with-meta `(logpoint ~id ~xform) (meta &form))))

(defmacro spy>
  ([x id] `(spy> ~x ~id identity))
  ([x id xform]
   (assert (keyword? id) "ID must be keyword")
   `(let [x# ~x]
      (enqueue! ~id ~xform x#)
      x#)))

(defmacro spy>>
  ([id x] `(spy>> ~id identity ~x))
  ([id xform x] `(spy> ~x ~id ~xform)))
