(ns prf.notebooks.shortfall-test-runner
  (:require [clojure.test :as test]
            [prf.notebooks.shortfall-accounting-test]))

(defn -main [& _]
  (let [result (test/run-tests 'prf.notebooks.shortfall-accounting-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
