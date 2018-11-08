(ns postmortem.specs.core
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

(s/fdef enqueue!
  :args (s/cat :id ::id :location ::location :vals ::vals))

(s/fdef filter-vals
  :args (s/cat :vals ::vals
               :targets (s/nilable (s/or :coll coll? :fn fn?))))
