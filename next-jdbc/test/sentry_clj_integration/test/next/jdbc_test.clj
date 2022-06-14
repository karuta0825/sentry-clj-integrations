(ns sentry-clj-integration.test.next.jdbc-test
  (:require
    [clojure.test :refer [use-fixtures]]
    [expectations.clojure.test :refer [defexpect expect expecting]]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [sentry-clj-integration.next.jdbc :refer [with-tracing]]
    [sentry-clj-integration.test.helper :as h]))

(def db {:dbtype "h2:mem" :dbname "example"})
(def ds (jdbc/get-datasource db))

(defn- make-test-data
  [f]
  ;; before
  (jdbc/execute! ds ["
create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255)
)"])

  (jdbc/execute! ds ["
insert into address(name,email)
  values('Sean Corfield','sean@corfield.org')"])

  ;; run test
  (f)
  ;; after
  (jdbc/execute! ds ["drop table address"]))

(use-fixtures :each make-test-data)

(defexpect execute!-test
  (expecting
    "when a select query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "select * from address"
          result (jdbc/execute! ds [query]
                                {:builder-fn rs/as-unqualified-lower-maps})]


      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect result [{:email "sean@corfield.org", :id 1, :name "Sean Corfield"}])))

  (expecting
    "when an insert query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "insert into address(name,email) values('karuta','karuta@github.com')"
          result (jdbc/execute! ds [query])]

      (expect 1 (count (.getChildren tr)))
      (expect query (.getDescription (nth (.getChildren tr) 0)))
      (expect [{:next.jdbc/update-count 1}] result)))

  (expecting
    "when a update query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "update address set name = ? where id = ?"
          params ["takayuki" 2]
          result (jdbc/execute! ds (into [query] params))]

      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect [{:next.jdbc/update-count 1}] result)))

  (expecting
    "when a delete query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "delete from address where id = ?"
          params [1]
          result (jdbc/execute! ds (into [query] params))]

      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect [{:next.jdbc/update-count 1}] result))))

(defexpect execute-one!-test
  (expecting
    "when a select query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "select * from address"
          result (jdbc/execute-one! ds [query]
                                    {:builder-fn rs/as-unqualified-lower-maps})]


      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect result {:email "sean@corfield.org", :id 1, :name "Sean Corfield"})))

  (expecting
    "when an insert query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "insert into address(name,email) values('karuta','karuta@github.com')"
          result (jdbc/execute-one! ds [query])]

      (expect 1 (count (.getChildren tr)))
      (expect query (.getDescription (nth (.getChildren tr) 0)))
      (expect {:next.jdbc/update-count 1} result)))

  (expecting
    "when a update query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "update address set name = ? where id = ?"
          params ["takayuki" 2]
          result (jdbc/execute-one! ds (into [query] params))]

      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect {:next.jdbc/update-count 1} result)))

  (expecting
    "when a delete query is executed, the query is traced"
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          query "delete from address where id = ?"
          params [1]
          result (jdbc/execute-one! ds (into [query] params))]

      (expect (count (.getChildren tr)) 1)
      (expect (.getDescription (nth (.getChildren tr) 0)) query)
      (expect {:next.jdbc/update-count 1} result))))

(defexpect transaction-test
  (expecting
    "when a query is executed in an transaction, the both transaction and query is traced."
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)
          result (jdbc/with-transaction [tx ds]
                                        (let [rtx (with-tracing tx)]
                                          (jdbc/execute! rtx ["update address set name = ? where id = ?" "karuta" 1])
                                          (jdbc/execute! rtx ["select * from address"])))]

      (expect (count (.getChildren tr)) 4)
      (expect ["START TRANSACTION" "update address set name = ? where id = ?" "select * from address" "COMMIT"]
              (map (fn [t] (.getDescription t)) (.getChildren tr)))
      (expect [{:ADDRESS/EMAIL "sean@corfield.org", :ADDRESS/ID 1, :ADDRESS/NAME "karuta"}] result)))

  (expecting
    "when executed queries fail, the rollback process is also executed."
    (let [tr (h/get-test-sentry-tracer)
          db {:dbtype "h2:mem" :dbname "example"}
          con (jdbc/get-datasource db)
          ds (with-tracing con)]
      (try
        (jdbc/with-transaction [tx ds]
                               (let [rtx (with-tracing tx)]
                                 (jdbc/execute! rtx ["delete from address where id = ?" 1])
                                 (throw (ex-info "Error!" {}))
                                 (jdbc/execute! rtx ["insert into address(name,email) values('kurta','kurata@github.com')"])))
        (catch Exception _))

      (let [result (jdbc/execute! con ["select * from address"])]
        (prn result)
        (expect (count (.getChildren tr)) 3)
        (expect ["START TRANSACTION" "delete from address where id = ?" "ROLLBACK"]
                (map (fn [t] (.getDescription t)) (.getChildren tr)))
        (expect [{:ADDRESS/EMAIL "sean@corfield.org", :ADDRESS/ID 1, :ADDRESS/NAME "karuta"}] result)))))
