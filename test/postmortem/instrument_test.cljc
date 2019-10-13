(ns postmortem.instrument-test
  (:require [clojure.test :refer [deftest is testing]]
            [postmortem.core :as pm]
            [postmortem.instrument :as pi]
            [postmortem.test-ns :as test-ns :refer [f g h]]
            [postmortem.xforms :as xf]))

(deftest ^:eftest/synchronized basic-workflow-test
  (is (= [`f] (pi/instrument `f)))
  (f 1)
  (f 2)
  (f 3)
  (is (= '[{:args (1)} {:args (1) :ret 2}
           {:args (2)} {:args (2) :ret 3}
           {:args (3)} {:args (3) :ret 4}]
         (pm/log-for `f)))
  (pm/reset!)

  (try (f -1) (f 0) (catch #?(:clj Throwable :cljs :default) _))
  (is (= `[{:args (-1)} {:args (-1) :ret 0} {:args (0)} {:args (0) :err ~test-ns/err}]
         (pm/log-for `f)))
  (pm/reset!)

  (is (= [`f] (pi/unstrument)))
  (f 1)
  (is (= nil (pm/log-for `f)))
  (pm/reset!))

(deftest ^:eftest/synchronized transducers-test
  (is (= [`f] (pi/instrument `f {:xform (filter (fn [{[x] :args}] (odd? x)))})))
  (mapv f [1 2 3 4 5])
  (is (= '[{:args (1)} {:args (1) :ret 2}
           {:args (3)} {:args (3) :ret 4}
           {:args (5)} {:args (5) :ret 6}]
         (pm/log-for `f)))
  (pm/reset!)

  (pi/instrument `f {:xform (xf/take-last 3)})
  (mapv f [1 2 3 4 5])
  (is (= '[{:args (4) :ret 5} {:args (5)} {:args (5) :ret 6}]
         (pm/log-for `f)))
  (pm/reset!)

  (is (= [`f] (pi/unstrument `f))))

(deftest ^:eftest/synchronized session-test
  (let [sess (pm/make-session)]
    (is (= [`f] (pi/instrument `f {:session sess})))
    (f 1)
    (f 2)
    (f 3)
    (is (= '[{:args (1)} {:args (1) :ret 2}
             {:args (2)} {:args (2) :ret 3}
             {:args (3)} {:args (3) :ret 4}]
           (pm/log-for sess `f)))
    (is (= nil (pm/log-for `f)))
    (is (= [`f] (pi/unstrument)))))

(deftest ^:eftest/synchronized multiple-fns-test
  (testing "instrument and unstrument accepts a coll of symbols"
    (is (= `[g h] (pi/instrument `[g h])))
    (g 3)
    (is (= '[{:args (3)} {:args (1)} {:args (1) :ret 2} {:args (3) :ret 8}]
           (pm/log-for `g)))
    (is (= '[{:args (2)} {:args (0)} {:args (0) :ret 0} {:args (2) :ret 6}]
           (pm/log-for `h)))
    (pm/reset!)
    (is (= `[g h] (pi/unstrument `[g h]))))

  (testing "If a symbol identifies ns, all symbols in that ns will be enumerated"
    (is (= `#{test-ns/f test-ns/g test-ns/h} (set (pi/instrument 'postmortem.test-ns))))
    (is (= `#{test-ns/f test-ns/g test-ns/h} (set (pi/unstrument 'postmortem.test-ns)))))

  (testing "xform will be applied to functions that were instrumented at once"
    (pi/instrument `[g h] {:xform (filter :ret)})
    (g 3)
    (is (= '[{:args (1) :ret 2} {:args (3) :ret 8}]
           (pm/log-for `g)))
    (is (= '[{:args (0) :ret 0} {:args (2) :ret 6}]
           (pm/log-for `h)))
    (pm/reset!)
    (is (= `[g h] (pi/unstrument))))

  (testing "session will be shared among functions that were instrumented at once"
    (let [sess (pm/make-session)]
      (pi/instrument `[g h] {:session sess})
      (g 3)
      (is (= '[{:args (3)} {:args (1)} {:args (1) :ret 2} {:args (3) :ret 8}]
             (pm/log-for sess `g)))
      (is (= '[{:args (2)} {:args (0)} {:args (0) :ret 0} {:args (2) :ret 6}]
             (pm/log-for sess `h)))
      (is (= nil (pm/log-for `g)))
      (is (= nil (pm/log-for `h)))
      (pi/unstrument))))
