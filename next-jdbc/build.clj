(ns build
  (:refer-clojure :exclude [test])
  (:require
    [clojure.tools.build.api :as b]
    ;; for b/git-count-revs
    [org.corfield.build :as bb]))

(def lib 'org.clojars.karuta/sentry-next-jdbc)
(def version "0.1.4")

#_ ; alternatively, use MAJOR.MINOR.COMMITS:
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn test
  "Run the tests."
  [opts]
  ;; (bb/run-tests opts)
  (-> (merge {:main-args ["-m" "kaocha.runner"]} opts)
      (bb/run-tests)))

(defn jar
  "Make a jar."
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (bb/jar)))

(defn ci
  "Run the CI pipeline of tests (and build the JAR)."
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install
  "Install the JAR locally."
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy
  "Deploy the JAR to Clojars."
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
