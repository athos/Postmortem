(ns postmortem.instrument.clj
  (:require [clojure.string :as str]
            [postmortem.instrument.core :as instr]
            [postmortem.utils :refer [with-lock]])
  #?@(:bb []
      :clj ((:import [java.util.concurrent.locks ReentrantLock]))))

#?(:bb
   (def instrument-lock (Object.))
   :clj
   (def ^:private ^ReentrantLock instrument-lock
     (ReentrantLock.)))

(defn- collectionize [x]
  (if (symbol? x)
    [x]
    x))

(defn- ->sym [v]
  (let [meta (meta v)]
    (symbol (name (ns-name (:ns meta))) (name (:name meta)))))

(defn- sym-or-syms->syms [sym-or-syms]
  (into []
        (mapcat (fn [sym]
                  (if (and (str/includes? (str sym) ".")
                           (find-ns sym))
                    (for [[_ v] (ns-interns sym)
                          :when (not (:macro (meta v)))]
                       (->sym v))
                    [sym])))
        (collectionize sym-or-syms)))

(defn- instrument-1 [sym opts]
  (when-let [v (resolve sym)]
    (let [var-name (->sym v)]
      (when-let [instrumented (instr/instrument-1* sym v opts)]
        (alter-var-root v (constantly instrumented))
        var-name))))

(defn- unstrument-1 [sym]
  (when-let [v (resolve sym)]
    (let [var-name (->sym v)]
      (when-let [raw (instr/unstrument-1* sym v)]
        (alter-var-root v (constantly raw))
        var-name))))

(defn instrument
  ([sym-or-syms] (instrument sym-or-syms {}))
  ([sym-or-syms opts]
   (with-lock instrument-lock
     (into []
           (comp (filter symbol?)
                 (distinct)
                 (map #(instrument-1 % opts))
                 (remove nil?))
           (sym-or-syms->syms sym-or-syms)))))

(defn unstrument
  ([] (unstrument (map ->sym (keys @instr/instrumented-vars))))
  ([sym-or-syms]
   (with-lock instrument-lock
     (into []
           (comp (filter symbol?)
                 (distinct)
                 (map unstrument-1)
                 (remove nil?))
           (sym-or-syms->syms sym-or-syms)))))
