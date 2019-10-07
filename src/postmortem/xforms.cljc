(ns postmortem.xforms
  (:refer-clojure :exclude [take-last drop-last]))

(defn take-until [pred]
  (fn [rf]
    (let [pred' (complement pred)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (if (pred' input)
           (rf result input)
           (reduced (rf result input))))))))

(defn drop-until [pred]
  (fn [rf]
    (let [dv (volatile! true)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (if @dv
           (do (when (pred input)
                 (vreset! dv false))
               result)
           (rf result input)))))))

(defn take-last
  ([] (take-last 1))
  ([^long n]
   (fn [rf]
     (let [idx (volatile! 0)
           vals (object-array n)]
       (fn
         ([] (rf))
         ([result]
          (let [i (long @idx)
                start (if (>= i n) i 0)
                end (if (>= i n) n i)]
            (transduce (map #(aget vals (rem (+ % start) n))) rf result (range end))))
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

(defn dedupe-by [f]
  (fn [rf]
    (let [prev (volatile! ::none)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([acc input]
         (let [p @prev
               v (f input)]
           (if (= p v)
             acc
             (do (vreset! prev v)
                 (rf acc input)))))))))

(defn- abs [x]
  #?(:clj (Math/abs x)
     :cljs (js/Math.abs x)))

(defn debounce
  ([interval] (debounce identity interval))
  ([f interval]
   (fn [rf]
     (let [prev (volatile! ::none)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([acc input]
          (let [p @prev
                v (f input)]
            (if (or (= p ::none)
                    (>= (abs (- v p)) interval))
              (do (vreset! prev v)
                  (rf acc input))
              acc))))))))
