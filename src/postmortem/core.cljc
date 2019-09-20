(ns postmortem.core
  (:refer-clojure :exclude [list])
  (:require [clojure.string :as str]))

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

(defn clear!
  ([& ids]
   (if (empty? ids)
     (reset! logs* {})
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

(defn- times [n c]
  (str/join (repeat n c)))

(defn- pad-left [s n]
  (str (times (- n (count s)) \space) s))

(defn- pad-right [s n]
  (str s (times (- n (count s)) \space)))

(defn- ndigits [n]
  (count (str n)))

(defn- make-hline [location-width id-width strategy-width count-width]
  (fn []
    (as-> [location-width id-width strategy-width count-width] <>
      (map #(str (times (+ % 2) \-)) <>)
      (str/join \+ <>)
      (str \+ <> \+)
      (println <>))))

(defn- header [location-width id-width strategy-width count-width]
  (println \| (pad-right "Location" location-width)
           \| (pad-right "ID" id-width)
           \| (pad-right "Strategy" strategy-width)
           \| (pad-right "Items" count-width) \|))

(defn list []
  (when (seq @logs*)
    (let [logs (sort-by (fn [[_ {:keys [location]}]]
                          [(:file location) (:line location) (:column location)])
                        @logs*)
          max-location (->> logs
                            (map (fn [[_ {{:keys [file line column]} :location}]]
                                   (+ (count file) (ndigits line) (ndigits column))))
                            (apply max 8)
                            (+ 2))
          max-id (apply max 2 (map (comp ndigits key) logs))
          max-strategy (->> logs
                            (map #(-> (val %) :strategy meta :form str count))
                            (apply max 8))
          max-count (->> logs
                         (map #(ndigits (count (:items (val %)))))
                         (apply max 5))
          hline (make-hline max-location max-id max-strategy max-count)]
      (hline)
      (header max-location max-id max-strategy max-count)
      (hline)
      (doseq [[id {:keys [location strategy items]}] logs]
        (println \| (-> (str (:file location) \:
                             (:line location) \:
                             (:column location))
                        (pad-right max-location))
                 \| (pad-right (str id) max-id)
                 \| (pad-right (str (:form (meta strategy) " -"))
                               max-strategy)
                 \| (pad-left (str (count items)) max-count) \|))
      (hline))))
