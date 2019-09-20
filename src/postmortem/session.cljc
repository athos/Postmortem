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

(defn- enqueue! [logs id xform item]
  (swap! logs update id
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

(defn- complete-logs! [logs ids]
  (swap! logs
         (fn [logs]
           (reduce-kv (fn [m id entry]
                        (if (contains? ids id)
                          (let [entry' (complete-log-entry! entry)]
                            (if (identical? entry entry')
                              m
                              (assoc m id entry')))
                          m))
                      logs
                      logs))))

(defn- collect-logs [logs ids]
  (reduce (fn [m k] (assoc m k (-> logs (get k) (get :items)))) {} ids))

(deftype Session [name logs]
  proto/ISession
  (-name [this] name)
  proto/ILogStorage
  (-add-item! [this id xform item]
    (enqueue! logs id xform item))
  (-logs [this]
    (complete-logs! logs (set (keys @logs)))
    (let [logs @logs]
      (collect-logs logs (keys logs))))
  (-logs [this ids]
    (complete-logs! logs ids)
    (collect-logs @logs ids))
  (-reset! [this]
    (reset! logs {}))
  (-reset! [this ids]
    (apply swap! logs dissoc ids)))
