(ns sentry-clj-integration.test.next.jdbc
  (:require
    [clojure.test :refer :all]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [sentry-clj-integration.next.jdbc :refer [with-tracing]]
    [sentry-clj.tracing :as sut])
  (:import
    (io.sentry
      Hub
      Sentry
      SentryOptions
      SentryTracer
      TransactionContext)))


;; ここでsetupのテストができるね。
;; clojureでいろいろなことができる。

(defn- get-test-options
  ([] (get-test-options {}))
  ([{:keys [traces-sample-rate]}]
   (let [sentry-option (SentryOptions.)]
     (.setDsn sentry-option "https://key@sentry.io/proj")
     (.setEnvironment sentry-option "development")
     (.setRelease sentry-option "release@1.0.0")
     (when traces-sample-rate
       (.setTracesSampleRate sentry-option traces-sample-rate))
     sentry-option)))


(defn- ^SentryTracer get-test-sentry-tracer
  []
  (let [sentry-option (get-test-options)
        hub (Hub. sentry-option)
        tr (SentryTracer. (TransactionContext. "name" "op" true) hub)]
    (Sentry/setCurrentHub hub)
    (.configureScope hub (reify io.sentry.ScopeCallback
                           (run
                             [_ scope]
                             (.setTransaction scope tr))))
    tr))


(def db {:dbtype "h2:mem" :dbname "example"})
(def ds (jdbc/get-datasource db))


(jdbc/execute! ds ["
create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255)
)"])


;; (jdbc/execute! ds ["
;; insert into address(name,email)
;;   values('Sean Corfield','sean@corfield.org')"])


;; (jdbc/execute! ds ["select * from address"]
;;                {:builder-fn rs/as-unqualified-lower-maps})

(deftest a-test
  (testing "FIXME, I fail."
    (let [tr (get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)]
      (jdbc/execute! ds ["select * from address"]
                     {:builder-fn rs/as-unqualified-lower-maps})

      (prn (jdbc/execute! ds ["select * from address"]
                          {:builder-fn rs/as-unqualified-lower-maps}))

      (prn (count (.getChildren tr)))
      (prn (.getDescription (nth (.getChildren tr) 0)))
      (is (= (nth (.getChildren tr) 0) 1))
      (.getChildren tr))))
