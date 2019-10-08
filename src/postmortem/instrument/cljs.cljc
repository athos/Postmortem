(ns postmortem.instrument.cljs
  (:require #?(:clj [net.cgrand.macrovich :as macros]))
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros]
                            [postmortem.instrument.cljs :refer [instrument unstrument]])))

(macros/deftime

  (defmacro instrument [& args] :instrument)

  (defmacro unstrument [& args] :unstrument)

  )
