(ns postmortem.core)

(def ^:private logs* (atom {}))

(defn logs []
  @logs*)

(defn clear!
  ([& ids]
   (if (empty? ids)
     (reset! logs* {})
     (apply swap! logs* dissoc ids))
   nil))

(defn enqueue! [id location vals]
  (swap! logs* update id
         (fn [entry]
           (-> entry
               (assoc :location location)
               (update :items (fnil conj []) vals)))))

(defn filter-vals [vals targets]
  (cond (nil? targets) vals
        (coll? targets)
        (if (empty? targets)
          vals
          (select-keys vals targets))
        :else (into {} (filter (comp targets key)) vals)))

(defmacro checkpoint
  ([id] (with-meta `(checkpoint ~id nil) (meta &form)))
  ([id targets]
   (let [location {:file *file*
                   :line (:line (meta &form))
                   :column (:column (meta &form))}
         vals (->> &env
                   (map (fn [[k v]] `[~(keyword k) ~k]))
                   (into {}))]
     `(enqueue! ~id ~location (filter-vals ~vals ~targets)))))
