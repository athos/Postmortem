(ns postmortem.protocols
  #?(:cljs (:refer-clojure :exclude [-reset!])))

(defprotocol ISession)

(defprotocol ILogStorage
  (-add-item! [this id xform item])
  (-logs [this] [this ids])
  (-reset! [this] [this ids]))
