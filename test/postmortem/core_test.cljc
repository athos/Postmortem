(ns postmortem.core-test
  (:require [clojure.test :refer [are deftest is testing]]
            [postmortem.core :as pm :refer [dump locals spy>>]]
            [postmortem.xforms :as xf]))

(deftest locals-test
  (is (= {:x :a :y 42}
         (let [x :a]
           (let [y 42]
             (locals)))))
  (let [f (fn [n] (locals))]
    (is (= {:n 42} (f 42))))
  (is (= {:x :a} (let [x :a y 42] (locals :x)))))

(defn add [a b]
  (dump :add)
  (spy>> :add-result (+ a b)))

(defn fib [n]
  (loop [n n a 0 b 1]
    (dump `fib)
    (if (= n 0)
      a
      (recur (dec n) b (add a b)))))

(deftest ^:eftest/synchronized basic-workflow-test
  (fib 5)
  (is (= #{:add :add-result `fib} (pm/keys)))
  (is (not (pm/completed? :add)))
  (is (= [{:a 0 :b 1}
          {:a 1 :b 1}
          {:a 1 :b 2}
          {:a 2 :b 3}
          {:a 3 :b 5}]
         (pm/log-for :add)))
  (is (pm/completed? :add))
  (is (= [1 2 3 5 8]
         (pm/log-for :add-result)))
  (is (= [{:n 5 :a 0 :b 1}
          {:n 4 :a 1 :b 1}
          {:n 3 :a 1 :b 2}
          {:n 2 :a 2 :b 3}
          {:n 1 :a 3 :b 5}
          {:n 0 :a 5 :b 8}]
         (pm/log-for `fib)))
  (is (= {:n 0 :a 5 :b 8} (pm/last-log-for `fib)))
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          :add-result [1 2 3 5 8]}
         (pm/logs-for #{:add :add-result})))
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          :add-result [1 2 3 5 8]
          `fib [{:n 5 :a 0 :b 1}
                {:n 4 :a 1 :b 1}
                {:n 3 :a 1 :b 2}
                {:n 2 :a 2 :b 3}
                {:n 1 :a 3 :b 5}
                {:n 0 :a 5 :b 8}]}
         (pm/logs)))
  (is (every? pm/completed? [:add :add-result `fib]))
  (is (pm/stats) {:add 5, :add-result 6, `fib 6})

  (pm/reset-key! :add-result)
  (is (= #{:add `fib} (pm/keys)))
  (is (= {:add [{:a 0 :b 1}
                {:a 1 :b 1}
                {:a 1 :b 2}
                {:a 2 :b 3}
                {:a 3 :b 5}]
          `fib [{:n 5 :a 0 :b 1}
                {:n 4 :a 1 :b 1}
                {:n 3 :a 1 :b 2}
                {:n 2 :a 2 :b 3}
                {:n 1 :a 3 :b 5}
                {:n 0 :a 5 :b 8}]}
         (pm/logs)))

  (pm/reset-keys! #{:add `fib})
  (is (= #{} (pm/keys)))
  (is (= {} (pm/logs)))
  (is (= {} (pm/stats))))

;; Assert this function definition compiles
;; cf. https://github.com/athos/postmortem/issues/2
(defn regression-2 [^long x]
  (pm/dump :regression-2))

(deftest ^:eftest/synchronized transducers-test
  (are [key xform expected]
      (let [f (fn [n]
                (loop [n n]
                  (dump key xform)
                  (if (= n 0)
                    n
                    (recur (dec n)))))]
        (f 10)
        (is (= expected (pm/log-for key))))

    :f1 identity [{:n 10} {:n 9} {:n 8} {:n 7} {:n 6}
                  {:n 5} {:n 4} {:n 3} {:n 2} {:n 1} {:n 0}]

    :f2 (map :n) [10 9 8 7 6 5 4 3 2 1 0]

    :f3 (comp (map :n) (xf/take-last 3)) [2 1 0]

    :f4 (comp (xf/take-last 3) (filter #(even? (:n %)))) [{:n 2} {:n 0}])

  (pm/reset!))

(deftest ^:eftest/synchronized session-test
  (testing "sessions can isolate multiple execution logs"
    (pm/reset!)
    (let [sess1 (pm/make-session)
          sess2 (pm/make-session)
          f1 (fn [n]
               (dump sess1 :f (map :n)))
          f2 (fn [n]
               (dump sess2 :f (map :n)))]
      (f1 5)
      (f2 100)
      (f1 10)
      (f2 200)
      (is (= {:f [5 10]} (pm/logs sess1)))
      (is (= {:f [100 200]} (pm/logs sess2)))
      (is (= {} (pm/logs)))))
  (testing "sessions can hold base transducer"
    (let [sess (pm/make-session (xf/take-last 3))
          f (fn [n]
              (dump sess :f-n (map :n))
              (spy>> sess :f-ret (filter even?) (inc n)))]
      (dotimes [i 10] (f i))
      (is (= [7 8 9] (pm/log-for sess :f-n)))
      (is (= [8 10] (pm/log-for sess :f-ret)))))
  (testing "set-current-session! destructively changes current session"
    (let [f (fn [x] (dump :f))
          old (pm/current-session)
          sess (pm/make-session)]
      (pm/set-current-session! sess)
      (f 42)
      (is (= {} (pm/logs old)))
      (is (= {:f [{:x 42}]} (pm/logs)))
      (is (= {:f [{:x 42}]} (pm/logs sess)))
      (pm/set-current-session! old)
      (is (= {} (pm/logs)))))
  (testing "with-session temporarily changes current session"
    (let [f (fn [x] (dump :f))
          sess (pm/make-session)]
      (pm/with-session sess
        (f 42))
      (is (= {} (pm/logs)))
      (is (= {:f [{:x 42}]} (pm/logs sess)))
      (pm/reset! sess)
      (f 43)
      (is (= {:f [{:x 43}]} (pm/logs)))
      (is (= {} (pm/logs sess)))
      (pm/reset!))))

(deftest unsafe-session-test
  (testing "unsafe-session can be used just as ordinary sessions in a single-threaded context"
    (let [sess (pm/make-unsafe-session)]
      (dotimes [i 10]
        (pm/spy>> sess :i identity i))
      (is (= [0 1 2 3 4 5 6 7 8 9] (pm/log-for sess :i)))))
  #?(:clj
     (testing "more than one simultaneous updates to a unsafe-session won't be synchronized"
       (let [sess (pm/make-unsafe-session)
             f (fn [] (pm/dump sess :f (map-indexed (fn [i _] (Thread/sleep 500) i))))]
         (run! deref [(future (f)) (future (f))])
         (is (= [0] ;; ideally, it must be [0 1]
                (pm/log-for sess :f)))))))

(deftest void-session-test
  (testing "void session never logs anything"
    (let [sess (pm/void-session)]
      (pm/spy>> sess :sum identity (+ 1 2))
      (is (= {} (pm/logs sess)))))
  (testing "void session never triggers a call to transducer"
    (let [sess (pm/void-session)
          f (fn [x] (pm/spy>> sess :f (comp (map (fn [x] (prn :pre x) x))
                                            (xf/take-last)
                                            (map (fn [x] (prn :post x) x)))
                              x))]
      (is (= "" (with-out-str (f 1) (f 2) (f 3))))
      (is (= "" (with-out-str (pm/logs sess)))))))

(deftest indexed-session-test
  (testing "indexed session logs items with auto-incremental index"
    (let [sess (pm/make-indexed-session)]
      (pm/spy>> sess :foo identity 100)
      (pm/spy>> sess :bar identity 101)
      (pm/spy>> sess :foo identity 102)
      (is (= {:foo [{:id 0 :val 100}
                    {:id 2 :val 102}]
              :bar [{:id 1 :val 101}]}
             (pm/logs sess)))))
  (testing "indexed session accepts an optional fn that specifies how to attach the given index to the item"
    (let [sess (pm/make-indexed-session vector)]
      (pm/spy>> sess :foo identity 100)
      (pm/spy>> sess :bar identity 101)
      (pm/spy>> sess :foo identity 102)
      (is (= {:foo [[0 100] [2 102]] :bar [[1 101]]}
             (pm/logs sess)))))
  (testing "calling reset! on an indexed session resets the index"
    (let [sess (pm/make-indexed-session)]
      (pm/spy>> sess :foo identity 100)
      (pm/spy>> sess :foo identity 101)
      (is (= [{:id 0 :val 100} {:id 1 :val 101}]
             (pm/log-for sess :foo)))
      (pm/reset-key! sess :foo)
      (pm/spy>> sess :foo identity 102)
      (pm/spy>> sess :foo identity 103)
      (is (= [{:id 2 :val 102} {:id 3 :val 103}]
             (pm/log-for sess :foo)))
      (pm/reset! sess)
      (is (= {} (pm/logs sess)))
      (pm/spy>> sess :foo identity 104)
      (pm/spy>> sess :foo identity 105)
      (is (= [{:id 0 :val 104} {:id 1 :val 105}]
             (pm/log-for sess :foo))))))

#?(:clj

   (deftest ^:eftest/synchronized synchronization-test
     (let [sess (pm/make-session)
           f (fn [n] (dump sess :f (comp (map-indexed #(assoc %2 :i %1))
                                         (xf/take-last))))
           futures [(future (dotimes [i 10000] (f i)))
                    (future (dotimes [i 10000] (f i)))]]
       (run! deref futures)
       (is (= 19999 (->> (pm/log-for sess :f) first :i)))))

   )

(deftest merged-logs-test
  (pm/with-session (pm/make-indexed-session)
    (pm/spy>> :foo :a)
    (pm/spy>> :bar :b)
    (pm/spy>> :foo :c)
    (is (= [{:id 0 :val :a}
            {:id 1 :val :b}
            {:id 2 :val :c}]
           (sort-by :id (pm/merged-logs)))))
  (pm/with-session (pm/make-indexed-session)
    (pm/spy>> :foo :a)
    (pm/spy>> :bar :b)
    (pm/spy>> :foo :c)
    (is (= [{:id 0 :key :foo :val :a}
            {:id 1 :key :bar :val :b}
            {:id 2 :key :foo :val :c}]
           (sort-by :id (pm/merged-logs #(assoc %2 :key %1)))))))

(deftest logger-test
  (let [f (pm/make-logger)]
    (is (= [0 1 2 3 4] (map f (range 5))))
    (is (= [0 1 2 3 4] (f)))
    (is (= 42 (f 42)))
    (is (= [0 1 2 3 4] (f))))
  (let [f (pm/make-logger (filter even?))]
    (is (= [0 1 2 3 4 5 6 7 8 9] (map f (range 10))))
    (is (= [0 2 4 6 8] (f)))))

(deftest multi-logger-test
  (let [f (pm/make-multi-logger)]
    (is (= 15
           (loop [n 5 sum 0]
             (if (= n 0)
               sum
               (recur (f :n (dec n)) (f :sum (+ sum n)))))))
    (is (= {:n [4 3 2 1 0], :sum [5 9 12 14 15]} (f)))
    (is (= [4 3 2 1 0] (f :n)))
    (is (= [5 9 12 14 15] (f :sum))))
  (let [f (pm/make-multi-logger (take 3))]
    (is (= 15
           (loop [n 5 sum 0]
             (if (= n 0)
               sum
               (recur (f :n (dec n)) (f :sum (+ sum n)))))))
    (is (= {:n [4 3 2], :sum [5 9 12]} (f)))
    (is (= [4 3 2] (f :n)))
    (is (= [5 9 12] (f :sum)))))
