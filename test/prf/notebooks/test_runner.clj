(ns prf.notebooks.test-runner
  (:require [clojure.test :as test]
            [prf.notebooks.runner-test]))

(defn -main [& _]
  (let [result (test/run-tests 'prf.notebooks.runner-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
