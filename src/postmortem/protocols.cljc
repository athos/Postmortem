(ns postmortem.protocols
  #?(:cljs (:refer-clojure :exclude [-name -reset!])))

(defprotocol ISession
  (-name [this]))

(defprotocol ILogStorage
  (-add-item! [this id xform item])
  (-logs [this] [this ids])
  (-reset! [this] [this ids]))
