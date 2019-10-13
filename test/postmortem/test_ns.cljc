(ns postmortem.test-ns)

(def err (ex-info "error!!" {}))

(defn f [x]
  (if (= x 0)
    (throw err)
    (inc x)))

(declare h)

(defn g [x]
  (if (= x 0)
    0
    (+ 2 (h (dec x)))))

(defn h [x]
  (if (= x 0)
    0
    (* 3 (g (dec x)))))
