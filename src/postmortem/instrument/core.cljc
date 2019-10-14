(ns postmortem.instrument.core
  (:require [postmortem.core :as pm]))

(defonce instrumented-vars (atom {}))

(defn- logging-fn [f fname {:keys [session xform]}]
  (let [xform (or xform identity)]
    (fn [& args]
      (let [session (or session (pm/current-session))]
        (pm/spy>> session fname xform {:args args})
        (let [thrown (volatile! nil)
              ret (try
                    (apply f args)
                    (catch #?(:clj Throwable :cljs :default) t
                      (vreset! thrown t)))]
          (if @thrown
            (do (pm/spy>> session fname xform {:args args :err @thrown})
                (throw @thrown))
            (do (pm/spy>> session fname xform {:args args :ret ret})
                ret)))))))

(defn instrument-1* [sym v opts]
  (let [{:keys [raw wrapped]} (get @instrumented-vars v)
        current @v
        to-wrapped (if (= wrapped current) raw current)]
    (when (fn? to-wrapped)
      (let [instrumented (logging-fn to-wrapped sym opts)]
        (swap! instrumented-vars assoc v
               {:raw to-wrapped :wrapped instrumented})
        instrumented))))

(defn unstrument-1* [sym v]
  (when-let [{:keys [raw wrapped]} (get @instrumented-vars v)]
    (swap! instrumented-vars dissoc v)
    (let [current @v]
      (when (= wrapped current)
        raw))))
