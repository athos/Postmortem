(ns postmortem.core
  (:refer-clojure :exclude [when first last])
  (:require [clojure.core :as cc]))

(def ^:private logs* (atom {}))

(defn logs []
  (reduce-kv (fn [m k v] (assoc m k (update v :items vec)))
             {}
             @logs*))

(defn clear!
  ([& ids]
   (if (empty? ids)
     (reset! logs* {})
     (apply swap! logs* dissoc ids))
   nil))

(defprotocol QueueingStrategy
  (-enqueue [this items vals]))

(defn enqueue [items vals]
  (conj items vals))

(defn enqueue! [id location vals strategy]
  (swap! logs* update id
         (fn [entry]
           (let [strategy (or (:strategy entry) strategy)]
             (-> entry
                 (assoc :location location :strategy strategy)
                 (update :items (fnil #(-enqueue strategy % vals) [])))))))

(defn filter-vals [vals targets]
  (cond (nil? targets) vals
        (coll? targets)
        (if (empty? targets)
          vals
          (select-keys vals targets))
        :else (into {} (filter (comp targets key)) vals)))

(declare all)

(defmacro checkpoint
  ([id]
   (with-meta `(checkpoint ~id nil) (meta &form)))
  ([id targets]
   (with-meta `(checkpoint ~id ~targets (all)) (meta &form)))
  ([id targets strategy]
   (let [location {:file *file*
                   :line (:line (meta &form))
                   :column (:column (meta &form))}
         vals (->> &env
                   (map (fn [[k v]] `[~(keyword k) ~k]))
                   (into {}))]
     `(enqueue! ~id ~location
                (with-meta (filter-vals ~vals ~targets)
                  {:time (System/nanoTime)})
                ~strategy))))

(defn all []
  (reify QueueingStrategy
    (-enqueue [this items vals]
      (enqueue items vals))))

(defn when
  ([pred] (when pred (all)))
  ([pred strategy]
   (reify QueueingStrategy
     (-enqueue [this items vals]
       (if (pred vals)
         (-enqueue strategy items vals)
         items)))))

(defn first
  ([] (first 1))
  ([n]
   (reify QueueingStrategy
     (-enqueue [this items vals]
       (if (< (count items) n)
         (enqueue items vals)
         items)))))

(defn last
  ([] (last 1))
  ([n]
   (reify QueueingStrategy
     (-enqueue [this items vals]
       (let [items (or (not-empty items) clojure.lang.PersistentQueue/EMPTY)]
         (cond-> (enqueue items vals)
           (>= (count items) n)
           pop))))))

(defn every [n]
  (let [i (volatile! 0)]
    (reify QueueingStrategy
      (-enqueue [this items vals]
        (let [ret (if (= @i 0)
                    (enqueue items vals)
                    items)]
          (vswap! i (fn [i] (rem (inc i) n)))
          ret)))))
