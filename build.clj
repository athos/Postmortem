(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'postmortem/postmortem)
(def version "0.5.2")
(def tag (b/git-process {:git-args "rev-parse HEAD"}))

(defn clean [opts]
  (bb/clean opts))

(defn jar [opts]
  (-> opts
      (assoc :src-pom "template/pom.xml"
             :lib lib :version version :scm {:tag tag})
      (clean)
      (bb/jar)))

(defn install [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
