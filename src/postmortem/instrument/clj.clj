(ns postmortem.instrument.clj
  (:require [postmortem.instrument.core :as instr]
            [postmortem.utils :refer [with-lock]])
  (:import [java.util.concurrent.locks ReentrantLock]))

(def ^:private ^ReentrantLock instrument-lock
  (ReentrantLock.))

(defn- collectionize [x]
  (if (symbol? x)
    [x]
    x))

(defn- ->sym [v]
  (let [meta (meta v)]
    (symbol (name (ns-name (:ns meta))) (name (:name meta)))))

(defn- instrument-1 [sym opts]
  (when-let [v (resolve sym)]
    (let [var-name (->sym v)
          instrumented (instr/instrument-1* sym v opts)]
      (when instrumented (alter-var-root v (constantly instrumented)))
      var-name)))

(defn- unstrument-1 [sym]
  (when-let [v (resolve sym)]
    (let [var-name (->sym v)
          raw (instr/unstrument-1* sym v)]
      (when raw (alter-var-root v (constantly raw)))
      var-name)))

(defn instrument
  ([sym-or-syms] (instrument sym-or-syms {}))
  ([sym-or-syms opts]
   (with-lock instrument-lock
     (into []
           (comp (filter symbol?)
                 (distinct)
                 (map #(instrument-1 % opts))
                 (remove nil?))
           (collectionize sym-or-syms)))))

(defn unstrument
  ([] (unstrument (map ->sym (keys @instr/instrumented-vars))))
  ([sym-or-syms]
   (with-lock instrument-lock
     (into []
           (comp (filter symbol?)
                 (distinct)
                 (map unstrument-1)
                 (remove nil?))
           (collectionize sym-or-syms)))))
