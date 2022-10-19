(ns postmortem.utils
  (:require #?(:clj [net.cgrand.macrovich :as macros]))
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros]
                            [postmortem.utils :refer [with-lock]])))

(macros/deftime

  ;; to avoid using the locking macro, which is problematic in some environments (see CLJ-1472)
  (defmacro with-lock [lock & body]
    (macros/case
     :clj
      #?(:bb
         `(locking ~lock ~@body)
         :clj
         `(let [^java.util.concurrent.locks.ReentrantLock lock# ~lock]
            (.lock lock#)
            (try
              ~@body
              (finally
                (.unlock lock#)))))
      :cljs
      `(do ~@body))))
