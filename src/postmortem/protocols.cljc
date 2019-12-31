(ns postmortem.protocols
  #?(:cljs (:refer-clojure :exclude [-reset!])))

(defprotocol ISession)

(defprotocol ILogStorage
  (-add-item! [this id xform item])
  (-keys [this])
  (-logs [this] [this keys])
  (-reset! [this] [this keys]))

(defprotocol ICompletable
  (-completed? [this key])
  (-complete! [this] [this keys]))
