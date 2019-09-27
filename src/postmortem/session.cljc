(ns postmortem.session
  (:require [postmortem.protocols :as proto])
  #?(:clj (:import [java.util.concurrent.locks ReentrantLock])))

(defn- xf->rf [xform]
  (let [rf (xform conj)
        finished? (volatile! false)]
    (fn
      ([] (rf))
      ([result]
       (vreset! finished? true)
       (rf result))
      ([acc item]
       (if @finished?
         acc
         (let [ret (rf acc item)]
           (when (reduced? ret)
             (vreset! finished? true))
           (unreduced ret)))))))

(defn- enqueue! [logs key base-xform xform item]
  (if-let [{:keys [items] :as entry} (get logs key)]
    (let [items' ((:fn entry) items item)]
      (if (identical? items items')
        logs
        (assoc logs key (assoc entry :items items'))))
    (let [rf (xf->rf (comp base-xform xform))
          entry {:items (rf (rf) item) :fn rf :completed? false}]
      (assoc logs key entry))))

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
  (->> keys
       (reduce (fn [m k] (assoc! m k (-> logs (get k) (get :items)))) (transient {}))
       persistent!))

(deftype ThreadUnsafeSession [xform ^:volatile-mutable logs]
  proto/ISession
  proto/ILogStorage
  (-add-item! [this key xform' item]
    (set! logs (enqueue! logs key xform xform' item)))
  (-logs [this]
    (collect-logs logs (keys logs)))
  (-logs [this keys]
    (collect-logs logs keys))
  (-reset! [this]
    (set! logs {}))
  (-reset! [this keys]
    (set! logs (apply dissoc logs keys)))
  proto/ICompletable
  (-completed? [this key]
    (-> logs (get key) (get :completed?)))
  (-complete! [this]
    (set! logs (complete-logs! logs (set (keys logs)))))
  (-complete! [this keys]
    (set! logs (complete-logs! logs keys))))

#?(:clj
   ;; to avoid using the locking macro, which is problematic in some environments (see CLJ-1472)
   (defmacro with-lock [lock & body]
     `(let [lock# ~lock]
        (.lock lock#)
        (try
          ~@body
          (finally
            (.unlock lock#))))))

#?(:clj
   (defn synchronized [session]
     (let [^ReentrantLock lock (ReentrantLock.)]
       (reify
         proto/ISession
         proto/ILogStorage
         (-add-item! [this key xform' item]
           (with-lock lock
             (proto/-add-item! session key xform' item)))
         (-logs [this]
           (with-lock lock
             (proto/-logs session)))
         (-logs [this keys]
           (with-lock lock
             (proto/-logs session keys)))
         (-reset! [this]
           (with-lock lock
             (proto/-reset! session)))
         (-reset! [this keys]
           (with-lock lock
             (proto/-reset! session keys)))
         proto/ICompletable
         (-completed? [this key]
           (with-lock lock
             (proto/-completed? session key)))
         (-complete! [this]
           (with-lock lock
             (proto/-complete! session)))
         (-complete! [this keys]
           (with-lock lock
             (proto/-complete! session keys)))))))
