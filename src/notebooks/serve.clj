(ns notebooks.serve
  (:require [nextjournal.clerk :as clerk]
            [clojure.java.io :as io]
            [resolver-sim.config.hardening :as hardening]
            [resolver-sim.logging :as log])
  (:import [java.nio.file Files LinkOption Path Paths]
           [java.nio.file.attribute FileAttribute]))

(defn- notebook-root []
  (if-let [resource (io/resource "notebooks/index.clj")]
    (.getParent (Paths/get (.toURI resource)))
    (throw (ex-info "Cannot locate extracted notebook sources on the classpath" {}))))

(defn- install-notebook-route! [^Path root]
  (let [cwd (.toAbsolutePath (Paths/get "." (make-array String 0)))
        route-root (.resolve cwd "notebooks")
        target (.toRealPath root (make-array LinkOption 0))]
    (if (Files/exists route-root (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (if (= target (.toRealPath route-root (make-array LinkOption 0)))
        false
        (throw (ex-info "Cannot expose extracted notebooks at ./notebooks: path already exists"
                        {:path (str route-root) :source (str target)})))
      (do
        (Files/createSymbolicLink route-root target (make-array FileAttribute 0))
        (log/info! "notebook/source-route-installed"
                   {:path (str route-root) :source (str target) :cleanup :jvm-shutdown})
        (.addShutdownHook
         (Runtime/getRuntime)
         (Thread. ^Runnable (fn [] (Files/deleteIfExists route-root))
                  "prf-notebooks-route-cleanup"))
        true))))

(defn- show-notebook! [^Path root filename]
  (let [path (.resolve root filename)
        route-path (str (.resolve (Paths/get "notebooks" (make-array String 0)) filename))]
    (if (.exists (.toFile path))
      (try
        (clerk/show! route-path)
        (catch Exception error
          ;; One legacy notebook may depend on a host feature absent from the
          ;; pinned PRF revision. Keep the server available for other pages.
          (log/warn! "notebook/server-evaluation-failed"
                     {:path route-path :error (.getMessage error)})))
      (log/warn! "notebook/server-skipping-missing" {:path (str path)}))))

(defn -main []
  (let [port (hardening/value :notebook-port {:fallback 7777})
        nrepl-port (hardening/value :notebook-api-port {:fallback 7778})
        root (notebook-root)]
    (install-notebook-route! root)
    (log/info! "notebook/server-starting" {:port port})
    (println (str "Starting Clerk notebook server on http://localhost:" port "/notebooks/xtdr_overview.clj"))
    (clerk/serve! {:watch-paths ["src" "data" "protocols_src" "notebooks"]
                   :browse true
                   :port port
                   :render-nrepl {:port nrepl-port}})
    ;; Pre-evaluate all notebooks so they are reachable by URL without a file-change trigger.
    ;; show! evaluates the file and registers it; the last call sets the default landing page.
    (show-notebook! root "xtdb_overview.clj")
    (show-notebook! root "invariant_failures.clj")
    (show-notebook! root "telemetry.clj")
    (show-notebook! root "report.clj")
    (show-notebook! root "workbench_v2.clj")
    (show-notebook! root "yield_scenarios_workbench.clj")
    (show-notebook! root "canonical_framing.clj")
    (show-notebook! root "equilibrium_artifact.clj")
    (show-notebook! root "dispute_resolution.clj")
    (show-notebook! root "withdrawal_observatory.clj")
    (show-notebook! root "game_theory_artifact.clj")
    (show-notebook! root "appeal_analysis.clj")
    (show-notebook! root "hardening_artifact.clj")
    (show-notebook! root "protocol_provenance.clj")
    (show-notebook! root "security_validation.clj")
    (show-notebook! root "benchmark_protocol_robustness.clj")
    (show-notebook! root "demo_not_admitted.clj")
    (show-notebook! root "demo_reorder_chain.clj")
    (show-notebook! root "demo_short_circuit.clj")
    (show-notebook! root "reachability_and_ratification.clj")

    ;; Index is shown last — it becomes the default landing page.
    (show-notebook! root "index.clj")))

