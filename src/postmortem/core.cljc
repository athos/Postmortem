(ns postmortem.core
  (:refer-clojure :exclude [when first last])
  (:require [postmortem.strategy :as strategy]))

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

(defn except [& ids]
  (let [ids (set ids)]
    (complement ids)))

(defn all []
  (strategy/all))

(defn when
  ([pred] (when pred (all)))
  ([pred strategy]
   (strategy/when pred strategy)))

(defn first
  ([] (first 1))
  ([n] (strategy/first n)))

(defn last
  ([] (last 1))
  ([n] (strategy/last n)))

(defn every [n]
  (strategy/every n))
