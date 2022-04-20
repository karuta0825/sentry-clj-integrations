(ns sentry-clj-integration.test.helper
  (:import
    (io.sentry
      Hub
      Sentry
      SentryOptions
      SentryTracer
      TransactionContext)))


;; ここでsetupのテストができるね。
;; clojureでいろいろなことができる。

(defn get-sentry-options
  ([] (get-sentry-options {}))
  ([{:keys [traces-sample-rate]}]
   (let [sentry-option (SentryOptions.)]
     (.setDsn sentry-option "https://key@sentry.io/proj")
     (.setEnvironment sentry-option "development")
     (.setRelease sentry-option "release@1.0.0")
     (when traces-sample-rate
       (.setTracesSampleRate sentry-option traces-sample-rate))
     sentry-option)))


(defn ^SentryTracer get-test-sentry-tracer
  []
  (let [sentry-option (get-sentry-options)
        hub (Hub. sentry-option)
        tr (SentryTracer. (TransactionContext. "name" "op" true) hub)]
    (Sentry/setCurrentHub hub)
    (.configureScope hub (reify io.sentry.ScopeCallback
                           (run
                             [_ scope]
                             (.setTransaction scope tr))))
    tr))
