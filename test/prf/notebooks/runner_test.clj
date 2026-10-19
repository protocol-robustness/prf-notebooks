(ns prf.notebooks.runner-test
  (:require [clojure.test :refer [deftest is testing]]
            [prf.notebooks.runner :as runner])
  (:import [java.nio.file Files Path Paths StandardOpenOption]
           [java.nio.file.attribute FileAttribute]
           [java.util.jar JarEntry JarOutputStream]))

(defn- temp-dir []
  (Files/createTempDirectory "prf-notebooks-test-" (make-array FileAttribute 0)))

(defn- write-source! [^Path root relative-path source]
  (let [path (.resolve root relative-path)]
    (Files/createDirectories (.getParent path) (make-array FileAttribute 0))
    (spit (.toFile path) source)
    path))

(defn- write-namespace! [^Path root relative-path]
  (write-source! root relative-path "(ns prf.canonical.fixture)\n"))

(defn- write-namespace-jar! [^Path path relative-path]
  (Files/createDirectories (.getParent path) (make-array FileAttribute 0))
  (with-open [jar (JarOutputStream. (Files/newOutputStream
                                     path (into-array java.nio.file.OpenOption
                                                      [StandardOpenOption/CREATE
                                                       StandardOpenOption/TRUNCATE_EXISTING
                                                       StandardOpenOption/WRITE])))]
    (.putNextEntry jar (JarEntry. relative-path))
    (.write jar (.getBytes "(ns prf.canonical.fixture)\n" "UTF-8"))
    (.closeEntry jar))
  path)

(deftest registered-notebooks-have-executable-sources
  (is (empty? (runner/registry-source-errors runner/repo-root runner/registry)))
  (is (= (set (keys runner/by-id)) (set (keys (:notebooks runner/config)))))
  (is (not (contains? runner/by-id :workbench-production)))
  (is (some #(= :workbench-production (:id %))
            (:archived-notebooks runner/registry))))

(deftest canonical-duplicate-check-groups-by-namespace-across-roots
  (let [first-root (temp-dir)
        second-root (temp-dir)]
    (write-namespace! first-root "prf/canonical/hash.clj")
    (write-namespace! second-root "prf/canonical/hash.clj")
    (is (= 2 (count (get (runner/namespace-inventory
                         [(str first-root) (str second-root)])
                        "prf/canonical/hash.clj"))))
    (is (= 2 (count (get (runner/duplicate-canonical-namespaces
                         [(str first-root) (str second-root)])
                        "prf/canonical/hash.clj"))))
    (is (empty? (runner/duplicate-canonical-namespaces [(str first-root)])))))

(deftest duplicate-notebook-support-sources-must-match
  (let [first-root (temp-dir)
        second-root (temp-dir)
        source-path "resolver_sim/notebook_support/theme.clj"
        source-a "(ns resolver-sim.notebook-support.theme)\n(def color :teal)\n"
        source-b "(ns resolver-sim.notebook-support.theme)\n(def color :blue)\n"
        matching (do (write-source! first-root source-path source-a)
                     (write-source! second-root source-path source-a)
                     (runner/namespace-inventory [(str first-root) (str second-root)]))]
    (is (empty? (runner/conflicting-notebook-source-copies matching)))
    (write-source! second-root source-path source-b)
    (let [divergent (runner/namespace-inventory [(str first-root) (str second-root)])]
      (is (= 2 (count (get divergent source-path))))
      (is (= 2 (count (get (runner/conflicting-notebook-source-copies divergent)
                           source-path)))))))

(deftest canonical-duplicate-check-includes-jars
  (let [source-root (temp-dir)
        jar-path (.resolve (temp-dir) "canonical.jar")]
    (write-namespace! source-root "prf/canonical/hash.clj")
    (write-namespace-jar! jar-path "prf/canonical/hash.clj")
    (is (= 2 (count (get (runner/duplicate-canonical-namespaces
                         [(str source-root) (str jar-path)])
                        "prf/canonical/hash.clj"))))))

(deftest setup-flags-reject-empty-and-unknown-options
  (is (= {:error :missing-options} (runner/setup-options [])))
  (is (= {:error :unknown-options :options #{"--surprise"}}
         (runner/setup-options ["--standalone" "--surprise"])))
  (is (= {:standalone true :prf-host false :packages false :all false}
         (runner/setup-options ["--standalone"]))))

(deftest host-classpath-excludes-repository-root-and-resolves-local-roots
  (let [host-root (Paths/get "/managed/prf-host/fixture" (make-array String 0))
        source-config {:paths ["." "src" "pro_rata_contract_src" "resources"]
                       :deps {'prf/accounting-reductions
                              {:local/root "packages/accounting-reductions/clj"}}}
        runtime-config (runner/host-dependency-config host-root source-config)]
    (is (= [(str (.resolve host-root "src"))
            (str (.resolve host-root "pro_rata_contract_src"))
            (str (.resolve host-root "resources"))
            (str (.resolve host-root "protocols_src"))]
           (:paths runtime-config)))
    (is (= (str (.resolve host-root "packages/accounting-reductions/clj"))
           (get-in runtime-config [:deps 'prf/accounting-reductions :local/root])))))

(deftest missing-registry-paths-are-reported
  (let [registry {:notebooks [{:id :missing :path "notebooks/absent.clj"}]}]
    (is (= [{:notebook/id :missing :notebook/path "notebooks/absent.clj"}]
           (runner/registry-source-errors runner/repo-root registry)))))

(deftest managed-gitlib-paths-are-recorded-portably
  (let [path (str (System/getProperty "user.home")
                  "/.gitlibs/libs/prf/canonical/abc123/clj/src")]
    (is (= "gitlibs:prf/canonical/abc123/clj/src"
           (runner/normalized-classpath-entry path)))))
