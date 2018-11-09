(ns postmortem.core
  (:refer-clojure :exclude [list when first last])
  (:require [clojure.core :as cc]
            [clojure.string :as str]
            [postmortem.strategy :as strategy]))

(def ^:private logs* (atom {}))

(defn logs
  ([]
   (reduce-kv (fn [m k v] (assoc m k (vec (:items v))))
              {}
              @logs*))
  ([id] (get (logs) id)))

(defn clear!
  ([& ids]
   (if (empty? ids)
     (reset! logs* {})
     (apply swap! logs* dissoc ids))
   nil))

(declare all)

(defn enqueue! [id location vals strategy]
  (swap! logs* update id
         (fn [entry]
           (let [e (cond-> entry
                     (nil? entry)
                     (assoc :location location
                            :strategy (or strategy (all))))]
             (update e :items (fnil #(strategy/-enqueue (:strategy e) % vals) []))))))

(defn filter-vals [vals targets]
  (cond (nil? targets) vals
        (coll? targets)
        (if (empty? targets)
          vals
          (select-keys vals targets))
        :else (into {} (filter (comp targets key)) vals)))

(defmacro checkpoint
  ([id]
   (with-meta `(checkpoint ~id nil) (meta &form)))
  ([id targets]
   (with-meta `(checkpoint ~id ~targets nil) (meta &form)))
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
                ~(cc/when strategy
                   `(with-meta ~strategy
                      {:form '~strategy}))))))

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
  (cc/when (seq @logs*)
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
                 \| (pad-right (str (:form (meta strategy) \-))
                               max-strategy)
                 \| (pad-left (str (count items)) max-count) \|))
      (hline))))
