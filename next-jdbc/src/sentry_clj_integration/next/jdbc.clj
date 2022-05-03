(ns sentry-clj-integration.next.jdbc
  (:require
    [next.jdbc.protocols :as p]
    [sentry-clj.tracing :as st]))

(defrecord SentryIntegrateSQL
  [connectable])

(extend-protocol p/Sourceable
  SentryIntegrateSQL
  (get-datasource [this]
    (p/get-datasource (:connectable this))))

(extend-protocol p/Connectable
  SentryIntegrateSQL
  (get-connection [this opts]
    (p/get-connection (:connectable this)
                      (merge (:options this) opts))))

(defn- get-query-helper
  [sql-params]
  (let [[sql] sql-params]
    sql))

(extend-protocol p/Executable
  SentryIntegrateSQL
  (-execute [this sql-params opts]
    (st/with-start-child-span "db" (get-query-helper sql-params)
                              (p/-execute (:connectable this) sql-params
                                          (merge (:options this) opts))))
  (-execute-one [this sql-params opts]
    (st/with-start-child-span "db" (get-query-helper sql-params)
                              (p/-execute-one (:connectable this) sql-params
                                              (merge (:options this) opts))))
  (-execute-all [this sql-params opts]
    (st/with-start-child-span "db" (get-query-helper sql-params)
                              (p/-execute-all (:connectable this) sql-params
                                              (merge (:options this) opts)))))

(extend-protocol p/Preparable
  SentryIntegrateSQL
  (prepare [this sql-params opts]
    (p/prepare (:connectable this) sql-params
               (merge (:options this) opts))))

(extend-protocol p/Transactable
  SentryIntegrateSQL
  (-transact [this body-fn opts]
    (st/with-start-child-span "db" "START TRANSACTION" (p/-transact (:connectable this) body-fn
                                                                    (merge (:options this) opts)))))

(defn with-tracing
  [connectable]
  (->SentryIntegrateSQL connectable))

