(ns postmortem.session
  (:require [postmortem.protocols :as proto])
  #?(:clj (:import [java.util.concurrent.locks ReentrantLock])))

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

(defn- enqueue! [logs key base-xform xform item]
  (update logs key
          (fn [entry]
            (as-> entry e
              (if (nil? e)
                (let [rf (xf->rf (comp base-xform xform))]
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

(deftype ThreadUnsafeSession [xform ^:unsynchronized-mutable logs]
  proto/ISession
  proto/ILogStorage
  (-add-item! [this key xform' item]
    (set! logs (enqueue! logs key xform xform' item)))
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

#?(:clj
   (defmacro with-lock [lock & body]
     `(let [lock# ~lock]
        (.lock lock#)
        (try
          ~@body
          (finally
            (.unlock lock#))))))

#?(:clj
   (deftype LockingSession [^ReentrantLock lock xform logs]
     proto/ISession
     proto/ILogStorage
     (-add-item! [this key xform' item]
       (with-lock lock
         (vswap! logs enqueue! key xform xform' item)))
     (-logs [this]
       (with-lock lock
         (vswap! logs complete-logs! (set (keys @logs))))
       (let [logs @logs]
         (collect-logs logs (keys logs))))
     (-logs [this keys]
       (with-lock lock
         (vswap! logs complete-logs! keys))
       (collect-logs @logs keys))
     (-reset! [this]
       (with-lock lock
         (vreset! logs {})))
     (-reset! [this keys]
       (with-lock lock
         (vreset! logs (apply dissoc @logs keys))))))
