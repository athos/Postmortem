(ns postmortem.core
  (:require [clojure.spec.alpha :as s]))

(s/def ::id keyword?)

(s/def ::file (s/nilable string?))
(s/def ::line (s/nilable int?))
(s/def ::column (s/nilable int?))
(s/def ::location (s/keys :req-un [::file ::line ::column]))

(s/def ::vals (s/map-of keyword? any?))
(s/def ::items (s/coll-of ::vals))

(s/def ::entry (s/keys :req-un [::location ::items]))

(s/def ::logs (s/map-of ::id ::entry))

(def ^:private logs* (atom {}))

(defn logs []
  @logs*)

(defn clear! []
  (reset! logs* {})
  nil)

(s/fdef enqueue!
  :args (s/cat :id ::id :location ::location :vals ::vals))

(defn enqueue! [id location vals]
  (swap! logs* update id
         (fn [entry]
           (-> entry
               (assoc :location location)
               (update :items (fnil conj []) vals)))))

(s/fdef filter-vals
  :args (s/cat :vals ::vals
               :targets (s/nilable (s/or :coll coll? :fn fn?))))

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
