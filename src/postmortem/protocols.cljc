(ns postmortem.protocols)

(defprotocol ISession
  (-name [this]))

(defprotocol ILogStorage
  (-add-item! [this id xform item])
  (-logs [this] [this ids])
  (-reset! [this] [this ids]))
