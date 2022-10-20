(ns postmortem.session
  (:require [postmortem.protocols :as proto]
            #?(:clj [postmortem.utils :refer [with-lock]]))
  #?@(:bb []
      :clj ((:import [java.util.concurrent.locks ReentrantLock]))))

(defn- xf->rf
  ([xform] (xf->rf xform conj))
  ([xform rf]
   (let [rf (xform rf)
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
            (unreduced ret))))))))

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
       (reduce (fn [m k]
                 (if-let [items (-> logs (get k) (get :items))]
                   (assoc! m k items)
                   m))
               (transient {}))
       persistent!))

(deftype ThreadUnsafeSession [xform ^:volatile-mutable logs]
  proto/ISession
  proto/ILogStorage
  (-add-item! [_ key xform' item]
    (set! logs (enqueue! logs key xform xform' item)))
  (-keys [_]
    (keys logs))
  (-logs [_]
    (collect-logs logs (keys logs)))
  (-logs [_ keys]
    (collect-logs logs keys))
  (-reset! [_]
    (set! logs {}))
  (-reset! [_ keys]
    (set! logs (apply dissoc logs keys)))
  proto/ICompletable
  (-completed? [_ key]
    (-> logs (get key) (get :completed?)))
  (-complete! [_]
    (set! logs (complete-logs! logs (set (keys logs)))))
  (-complete! [_ keys]
    (set! logs (complete-logs! logs keys))))

(defn void-session []
  (reify
    proto/ISession
    proto/ILogStorage
    (-add-item! [_ _key _xform _item])
    (-keys [_])
    (-logs [_] {})
    (-logs [_ _keys] {})
    (-reset! [_])
    (-reset! [_ _keys])
    proto/ICompletable
    (-completed? [_ _key] true)
    (-complete! [_])
    (-complete! [_ _keys])))

(defn indexed [session f]
  (let [id (atom -1)]
    (reify
      proto/ISession
      proto/ILogStorage
      (-add-item! [_ key xform item]
        (proto/-add-item! session key xform (f (swap! id inc) item)))
      (-keys [_]
        (proto/-keys session))
      (-logs [_]
        (proto/-logs session))
      (-logs [_ keys]
        (proto/-logs session keys))
      (-reset! [_]
        (proto/-reset! session)
        (reset! id -1)
        nil)
      (-reset! [_ keys]
        (proto/-reset! session keys))
      proto/ICompletable
      (-completed? [_ key]
        (proto/-completed? session key))
      (-complete! [_]
        (proto/-complete! session))
      (-complete! [_ keys]
        (proto/-complete! session keys)))))

#?(:cljs (do)
   :default
   (deftype SynchronizedSession [session lock]
     proto/ISession
     proto/ILogStorage
     (-add-item! [_ key xform' item]
       (with-lock lock
         (proto/-add-item! session key xform' item)))
     (-keys [_]
       (with-lock lock
         (proto/-keys session)))
     (-logs [_]
       (with-lock lock
         (proto/-logs session)))
     (-logs [_ keys]
       (with-lock lock
         (proto/-logs session keys)))
     (-reset! [_]
       (with-lock lock
         (proto/-reset! session)))
     (-reset! [_ keys]
       (with-lock lock
         (proto/-reset! session keys)))
     proto/ICompletable
     (-completed? [_ key]
       (with-lock lock
         (proto/-completed? session key)))
     (-complete! [_]
       (with-lock lock
         (proto/-complete! session)))
     (-complete! [_ keys]
       (with-lock lock
         (proto/-complete! session keys)))))

#?(:bb
   (defn synchronized [session]
     (->SynchronizedSession session (Object.)))
   :clj
   (defn synchronized [session]
     (->SynchronizedSession session (ReentrantLock.))))
