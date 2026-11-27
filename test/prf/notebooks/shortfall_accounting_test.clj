(ns prf.notebooks.shortfall-accounting-test
  (:require [clojure.test :refer [deftest is]]
            [notebooks.util.yield-demo-sq1 :as demo]))

(def deposits [{:event :deposit :user :alice :amount 1000}
               {:event :deposit :user :bob :amount 750}
               {:event :deposit :user :cara :amount 500}
               {:event :set-liquidity-shortfall :available-ratio 0.4}])

(def first-wave (into deposits
  [{:event :withdraw-request :user :alice :right/id "a" :request-kind :opening :requested 1000}
   {:event :withdraw-request :user :bob :right/id "b" :request-kind :opening :requested 500}
   {:event :withdraw-request :user :cara :right/id "c" :request-kind :opening :requested 500}]))

(deftest first-wave-conserves-and-separates-unrequested-principal
  (let [summary (demo/accounting-summary (demo/run-scenario first-wave))]
    (is (= 2250 (:deposited summary)))
    (is (= 2000 (:opening-requested summary)))
    (is (= 900 (:cumulative-paid summary)))
    (is (= 1100 (:unpaid-requested-rights summary)))
    (is (= 250 (:unrequested-principal summary)))
    (is (= 1350 (:outstanding-principal summary)))
    (is (:principal-conserved? summary))
    (is (:outstanding-partitioned? summary))))

(deftest retries-change-one-right-and-new-request-opens-a-distinct-right
  (let [events (into first-wave
                 [{:event :set-liquidity-shortfall :available-ratio 0.9}
                  {:event :withdraw-request :user :alice :right/id "a" :request-kind :retry :requested 600}
                  {:event :withdraw-request :user :bob :right/id "b" :request-kind :retry :requested 200}
                  {:event :withdraw-request :user :bob :right/id "b-new" :request-kind :new :requested 250}
                  {:event :withdraw-request :user :cara :right/id "c" :request-kind :retry :requested 300}])
        result (demo/run-scenario events)
        summary (demo/accounting-summary result)]
    (is (= 250 (:newly-requested summary)))
    (is (= 1100 (:retry-attempts summary)))
    (is (= 2025 (:cumulative-paid summary)))
    (is (= 225 (:outstanding-principal summary)))
    (is (= 225 (:unpaid-requested-rights summary)))
    (is (= 0 (:unrequested-principal summary)))
    (is (= [500 200 193 232] (mapv :filled (drop 3 (:outcomes result)))))
    (is (= 100 (get-in summary [:rights "a" :outstanding])))
    (is (= 0 (get-in summary [:rights "b" :outstanding])))
    (is (= 57 (get-in summary [:rights "b-new" :outstanding])))
    (is (= 68 (get-in summary [:rights "c" :outstanding])))
    (is (:principal-conserved? summary))))

(deftest rejects-duplicate-right-ids
  (is (thrown? clojure.lang.ExceptionInfo
        (demo/run-scenario (into first-wave
          [{:event :withdraw-request :user :bob :right/id "b" :request-kind :new :requested 1}])))))

(deftest zero-liquidity-attempt-preserves-right-for-later-retry
  (let [events (into first-wave
                 [{:event :set-liquidity-shortfall :available-ratio 0.4}
                  {:event :withdraw-request :user :bob :right/id "b" :request-kind :retry :requested 200}
                  {:event :set-liquidity-shortfall :available-ratio 0.9}
                  {:event :withdraw-request :user :bob :right/id "b" :request-kind :retry :requested 200}])
        result (demo/run-scenario events)
        summary (demo/accounting-summary result)]
    (is (= [0 200] (mapv :filled (take-last 2 (:outcomes result)))))
    (is (= 0 (get-in summary [:rights "b" :outstanding])))
    (is (= 250 (:unrequested-principal summary)))
    (is (:principal-conserved? summary))))

(deftest full-recovery-can-clear-unique-retry-and-new-rights
  (let [events (into first-wave
                 [{:event :set-liquidity-shortfall :available-ratio 1.0}
                  {:event :withdraw-request :user :alice :right/id "a" :request-kind :retry :requested 600}
                  {:event :withdraw-request :user :bob :right/id "b" :request-kind :retry :requested 200}
                  {:event :withdraw-request :user :bob :right/id "b-new" :request-kind :new :requested 250}
                  {:event :withdraw-request :user :cara :right/id "c" :request-kind :retry :requested 300}])
        summary (demo/accounting-summary (demo/run-scenario events))]
    (is (= 2250 (:cumulative-paid summary)))
    (is (= 0 (:outstanding-principal summary)))
    (is (= 0 (:unpaid-requested-rights summary)))
    (is (= 0 (:unrequested-principal summary)))
    (is (:principal-conserved? summary))))

(deftest cannot-open-a-right-larger-than-unrequested-principal
  (is (thrown? clojure.lang.ExceptionInfo
        (demo/run-scenario (into first-wave
          [{:event :withdraw-request :user :bob :right/id "b-new" :request-kind :new :requested 251}])))))
