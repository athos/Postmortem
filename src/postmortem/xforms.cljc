(ns postmortem.xforms
  (:refer-clojure :exclude [take-last drop-last]))

(defn take-last
  ([] (take-last 1))
  ([^long n]
   (fn [rf]
     (let [idx (volatile! 0)
           vals (object-array n)]
       (fn
         ([] (rf))
         ([result]
          (let [offset (if (>= @idx n) (long @idx) 0)]
            (transduce (map #(aget vals (rem (+ % offset) n))) rf result (range n))))
         ([acc input]
          (aset vals (rem @idx n) input)
          (vswap! idx inc)
          acc))))))

(defn drop-last
  ([] (drop-last 1))
  ([^long n]
   (fn [rf]
     (let [vals (volatile! (transient []))]
       (fn
         ([] (rf))
         ([result]
          (let [m (max (- (count @vals) n) 0)]
            (transduce (map #(@vals %)) rf result (range m))))
         ([acc input]
          (vswap! vals conj! input)
          acc))))))

(defn debounce
  ([interval] (debounce identity interval))
  ([f interval]
   (fn [rf]
     (let [prev (volatile! nil)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([acc input]
          (let [p @prev
                v (f input)]
            (if (or (nil? p) (>= (- v p) interval))
              (do (vreset! prev v)
                  (rf acc input))
              acc))))))))
