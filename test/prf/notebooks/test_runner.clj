(ns prf.notebooks.test-runner
  (:require [clojure.test :as test]
            [prf.notebooks.runner-test]
            [prf.notebooks.host-load-check-test]))

(defn -main [& _]
  (let [result (test/run-tests 'prf.notebooks.runner-test
                              'prf.notebooks.host-load-check-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
