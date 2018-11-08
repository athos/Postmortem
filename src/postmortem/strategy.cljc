(ns postmortem.strategy
  (:refer-clojure :exclude [when first last]))

(defprotocol QueueingStrategy
  (-enqueue [this items vals]))

(defn enqueue [items vals]
  (conj items vals))

(defn all []
  (reify QueueingStrategy
    (-enqueue [this items vals]
      (enqueue items vals))))

(defn when [pred strategy]
  (reify QueueingStrategy
    (-enqueue [this items vals]
      (if (pred vals)
        (-enqueue strategy items vals)
        items))))

(defn first [n]
  (reify QueueingStrategy
    (-enqueue [this items vals]
      (if (< (count items) n)
        (enqueue items vals)
        items))))

(defn last [n]
  (reify QueueingStrategy
    (-enqueue [this items vals]
      (let [items (or (not-empty items) clojure.lang.PersistentQueue/EMPTY)]
        (cond-> (enqueue items vals)
          (>= (count items) n)
          pop)))))

(defn every [n]
  (let [i (volatile! 0)]
    (reify QueueingStrategy
      (-enqueue [this items vals]
        (let [ret (if (= @i 0)
                    (enqueue items vals)
                    items)]
          (vswap! i (fn [i] (rem (inc i) n)))
          ret)))))
