(ns postmortem.session
  (:require [postmortem.protocols :as proto]))

(defn- xf->rf [xform]
  (let [rf (xform conj)
        finished? (volatile! false)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([acc item]
       (if @finished?
         acc
         (let [ret (rf acc item)]
           (when (reduced? ret)
             (vreset! finished? true))
           (unreduced ret)))))))

(defn- enqueue! [logs key xform item]
  (update logs key
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

(defn- complete-log-entry! [entry]
  (if (:completed? entry)
    entry
    (-> entry
        (update :items (:fn entry))
        (assoc :completed? true))))

(defn- complete-logs! [logs keys]
  (reduce-kv (fn [m key entry]
               (if (contains? keys key)
                 (let [entry' (complete-log-entry! entry)]
                   (if (identical? entry entry')
                     m
                     (assoc m key entry')))
                 m))
             logs
             logs))

(defn- collect-logs [logs keys]
  (reduce (fn [m k] (assoc m k (-> logs (get k) (get :items)))) {} keys))

(deftype ThreadUnsafeSession [name ^:unsynchronized-mutable logs]
  proto/ISession
  (-name [this] name)
  proto/ILogStorage
  (-add-item! [this key xform item]
    (set! logs (enqueue! logs key xform item)))
  (-logs [this]
    (set! logs (complete-logs! logs (set (keys logs))))
    (collect-logs logs (keys logs)))
  (-logs [this keys]
    (set! logs (complete-logs! logs keys))
    (collect-logs logs keys))
  (-reset! [this]
    (set! logs {}))
  (-reset! [this keys]
    (set! logs (apply dissoc logs keys))))
