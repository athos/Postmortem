(ns postmortem.instrument.core
  (:require [postmortem.core :as pm]))

(defonce instrumented-vars (atom {}))

(def ^:private ^:dynamic *depth* 0)

(defn- logging-fn [f fname {:keys [session xform with-depth]}]
  (let [xform (or xform identity)
        sess (if session (constantly session) pm/current-session)
        log-item (if with-depth
                   #(assoc % :depth *depth*)
                   identity)
        save #(pm/spy>> (sess) fname xform (log-item %))
        f (fn [args]
            (save {:args args})
            (let [thrown (volatile! nil)
                  ret (try
                        (apply f args)
                        (catch #?(:clj Throwable :cljs :default) t
                          (vreset! thrown t)))]
              (if @thrown
                (do (save {:args args :err @thrown})
                    (throw @thrown))
                (do (save {:args args :ret ret})
                    ret))))]
    (if with-depth
      (fn [& args]
        (binding [*depth* (inc *depth*)]
          (f args)))
      (fn [& args]
        (f args)))))

(defn instrument-1* [sym v opts]
  (let [{:keys [raw wrapped]} (get @instrumented-vars v)
        current @v
        to-wrapped (if (= wrapped current) raw current)]
    (when (or (fn? to-wrapped)
              (instance? #?(:clj clojure.lang.MultiFn
                            :cljs cljs.core.MultiFn)
                         to-wrapped))
      (let [instrumented (logging-fn to-wrapped sym opts)]
        (swap! instrumented-vars assoc v
               {:raw to-wrapped :wrapped instrumented})
        instrumented))))

(defn unstrument-1* [_sym v]
  (when-let [{:keys [raw wrapped]} (get @instrumented-vars v)]
    (swap! instrumented-vars dissoc v)
    (let [current @v]
      (when (= wrapped current)
        raw))))
