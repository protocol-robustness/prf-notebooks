(ns prf.notebooks.lint
  "Lint notebook sources. PRF host support is linted by the host repository." 
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.nio.file Paths]))

(defn -main [& _]
  (let [resource (io/resource "notebooks/index.clj")]
    (when-not resource
      (throw (ex-info "Extracted notebook sources are missing; select the :notebooks alias." {})))
    (let [notebook-root (.getParent (.getParent (Paths/get (.toURI resource))))
          {:keys [exit out err] :as result}
          (shell/sh "clojure" "-M:lint/notebooks" "-m" "clj-kondo.main"
                    "--lint" (str (.resolve notebook-root "notebooks"))
                    "--fail-level" "error")]
      (when (seq out) (print out))
      (when (seq err) (binding [*out* *err*] (print err)))
      (when-not (zero? exit)
        (throw (ex-info "Notebook lint failed" {:result result})))))
  (shutdown-agents))
