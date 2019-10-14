(ns postmortem.instrument.cljs
  (:require [cljs.analyzer.api :as ana]
            [clojure.string :as str]
            #?(:clj [net.cgrand.macrovich :as macros])
            [postmortem.instrument.core :as instr])
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros]
                            [postmortem.instrument.cljs :refer [instrument unstrument]])))

(macros/deftime

  (def instrumented-var-names (atom #{}))

  (defmacro instrument-1 [[_ s] opts]
    (when-let [v (ana/resolve &env s)]
      (let [var-name (:name v)]
        (swap! instrumented-var-names conj var-name)
        `(when-let [instrumented# (instr/instrument-1* '~s (var ~s) ~opts)]
           (set! ~s instrumented#)
           '~var-name))))

  (defmacro unstrument-1 [[_ s]]
    (when-let [v (ana/resolve &env s)]
      (let [var-name (:name v)]
        (when (@instrumented-var-names var-name)
          (swap! instrumented-var-names disj var-name)
          `(when-let [raw# (instr/unstrument-1* '~s (var ~s))]
             (set! ~s raw#)
             '~var-name)))))

  (defn- form->sym-or-syms [sym-or-syms]
    (if (::no-eval (meta sym-or-syms))
      (second sym-or-syms)
      (eval sym-or-syms)))

  (defn- collectionize [sym-or-syms]
    (if (symbol? sym-or-syms)
      (list sym-or-syms)
      sym-or-syms))

  (defn- sym-or-syms->syms [sym-or-syms]
    (into []
          (mapcat (fn [sym]
                    (if (and (str/includes? (str sym) ".")
                             (ana/find-ns sym))
                      (for [[_ v] (ana/ns-interns sym)
                            :when (not (:macro v))]
                        (:name v))
                      [sym])))
          (collectionize sym-or-syms)))

  (defmacro instrument
    ([sym-or-syms] `(instrument ~sym-or-syms {}))
    ([sym-or-syms opts]
     (let [opts-sym (gensym)]
       `(let [~opts-sym ~opts]
          (into [] (comp (map (fn [f#] (f#)))
                         (remove nil?))
                [~@(for [sym (sym-or-syms->syms (form->sym-or-syms sym-or-syms))
                         :when (symbol? sym)]
                     `#(instrument-1 '~sym ~opts-sym))])))))

  (defmacro unstrument
    ([] `(unstrument ^::no-eval '[~@@instrumented-var-names]))
    ([sym-or-syms]
     `(into [] (comp (map (fn [f#] (f#)))
                     (remove nil?))
            [~@(for [sym (sym-or-syms->syms (form->sym-or-syms sym-or-syms))
                     :when (symbol? sym)]
                 `#(unstrument-1 '~sym))])))

  )
