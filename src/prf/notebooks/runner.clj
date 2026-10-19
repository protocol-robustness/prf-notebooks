(ns prf.notebooks.runner
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.set :as set]
            [clojure.pprint :as pprint]
            [clojure.string :as str])
  (:import [java.nio.file Files Path Paths LinkOption CopyOption StandardCopyOption]
           [java.util.jar JarFile]
           [java.security MessageDigest]))

(defn fail! [message]
  (binding [*out* *err*] (println message))
  (System/exit 2))

(defn- discover-repo-root []
  (if-let [configured (System/getenv "PRF_NOTEBOOKS_REPO")]
    (-> (Paths/get configured (make-array String 0)) .toAbsolutePath .normalize)
    (if-let [source (io/resource "notebooks/index.clj")]
      (-> (Paths/get (.toURI source)) .getParent .getParent .toAbsolutePath .normalize)
      (fail! "Cannot locate prf-notebooks source; set PRF_NOTEBOOKS_REPO."))))
(def repo-root (discover-repo-root))
(def cache-root
  (-> (Paths/get (or (System/getenv "PRF_NOTEBOOKS_CACHE")
                     (str (System/getProperty "user.home") "/.cache/prf-notebooks"))
                 (make-array String 0))
      .toAbsolutePath .normalize))
(def config
  (edn/read-string (slurp (.toFile (.resolve repo-root "data/notebook-runtime.edn")))))
(def registry
  (edn/read-string (slurp (.toFile (.resolve repo-root "data/notebooks.edn")))))
(def deps-config
  (edn/read-string (slurp (.toFile (.resolve repo-root "deps.edn")))))
(def by-id (into {} (map (juxt :id identity) (:notebooks registry))))

(defn registry-source-errors [root notebook-registry]
  (->> (:notebooks notebook-registry)
       (keep (fn [{:keys [id path]}]
               (when-not (and (string? path)
                              (Files/isRegularFile (.resolve ^Path root path)
                                                   (make-array LinkOption 0)))
                 {:notebook/id id :notebook/path path})))
       vec))

(defn setup-options [args]
  (let [valid #{"--standalone" "--prf-host" "--packages" "--all"}
        supplied (set args)
        unknown (set/difference supplied valid)]
    (cond
      (seq unknown) {:error :unknown-options :options unknown}
      (empty? supplied) {:error :missing-options}
      :else {:standalone (contains? supplied "--standalone")
             :prf-host (contains? supplied "--prf-host")
             :packages (contains? supplied "--packages")
             :all (contains? supplied "--all")})))

(defn namespace-inventory [classpath-paths]
  (let [namespace-path? #(or (re-matches #"prf/canonical/[^/]+\.clj" %)
                             (re-matches #"resolver_sim/notebook_support/.+\.clj" %)
                             (re-matches #"notebooks/.+\.clj" %))
        directory-entries
        (mapcat (fn [entry]
                  (let [root (Paths/get (str entry) (make-array String 0))]
                    (if (Files/isDirectory root (make-array LinkOption 0))
                      (for [file (file-seq (.toFile root))
                            :when (and (.isFile file)
                                       (namespace-path?
                                        (str/replace
                                         (str (.relativize root (.toPath file)))
                                         "\\" "/")))]
                        [(str/replace (str (.relativize root (.toPath file))) "\\" "/")
                         (str (.toAbsolutePath (.toPath file)))])
                      [])))
                classpath-paths)
        jar-entries
        (mapcat (fn [entry]
                  (let [root (Paths/get (str entry) (make-array String 0))]
                    (if (and (Files/isRegularFile root (make-array LinkOption 0))
                             (str/ends-with? (str root) ".jar"))
                      (with-open [jar (JarFile. (.toFile root))]
                        (vec (for [item (enumeration-seq (.entries jar))
                                   :let [name (.getName item)]
                                   :when (and (not (.isDirectory item)) (namespace-path? name))]
                               [name (str (.toAbsolutePath root) "!/" name)])))
                      [])))
                classpath-paths)]
    (->> (concat directory-entries jar-entries)
         (group-by first)
         (into {} (map (fn [[namespace sources]]
                         [namespace (mapv second sources)]))))))

(defn duplicate-canonical-namespaces [classpath-paths]
  (into {} (filter (fn [[namespace sources]]
                     (and (str/starts-with? namespace "prf/canonical/")
                          (> (count sources) 1)))
                   (namespace-inventory classpath-paths))))

(defn source-fingerprint [source]
  (let [separator (.lastIndexOf ^String source "!/")
        bytes (if (neg? separator)
                (Files/readAllBytes (Paths/get source (make-array String 0)))
                (let [jar-path (subs source 0 separator)
                      entry-name (subs source (+ separator 2))]
                  (with-open [jar (JarFile. jar-path)]
                    (if-let [entry (.getEntry jar entry-name)]
                      (with-open [stream (.getInputStream jar entry)]
                        (.readAllBytes stream))
                      (throw (ex-info "Classpath JAR entry disappeared"
                                      {:source source}))))))
        digest (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and 0xff %)) (.digest digest bytes)))))

(defn conflicting-notebook-source-copies [namespace-sources]
  (into {}
        (filter (fn [[_ sources]]
                  (> (count (set (map source-fingerprint sources))) 1)))
        (filter (fn [[namespace sources]]
                  (and (or (str/starts-with? namespace "resolver_sim/notebook_support/")
                           (str/starts-with? namespace "notebooks/"))
                       (> (count sources) 1)))
                namespace-sources)))

(defn git-sha [alias coordinate]
  (get-in deps-config [:aliases alias :extra-deps coordinate :git/sha]))

(defn validate-config! []
  (let [registered (set (keys by-id))
        classified (set (keys (:notebooks config)))
        expected {:prf-host (select-keys (:prf-host config) [:git/url :git/sha])
                  :canonical (select-keys (get-in config [:packages :canonical]) [:git/url :git/sha])
                  :allocation (select-keys (get-in config [:packages :allocation]) [:git/url :git/sha])}
        actual {:prf-host {:git/url (get-in deps-config [:aliases :prf-host :extra-deps
                                                          'protocol-robustness/framework :git/url])
                           :git/sha (git-sha :prf-host 'protocol-robustness/framework)}
                :canonical {:git/url (get-in deps-config [:aliases :portable-packages :extra-deps
                                                           'prf/canonical :git/url])
                            :git/sha (git-sha :portable-packages 'prf/canonical)}
                :allocation {:git/url (get-in deps-config [:aliases :portable-packages :extra-deps
                                                            'prf/pro-rata-allocation :git/url])
                             :git/sha (git-sha :portable-packages 'prf/pro-rata-allocation)}}
        actual (into {} (map (fn [[k v]] [k (into {} (remove (comp nil? val)) v)]) actual))]
    (when-not (= registered classified)
      (fail! (str "Notebook classifications do not match registry. Unclassified: "
                  (pr-str (set/difference registered classified))
                  "; unknown: " (pr-str (set/difference classified registered)))))
    (when-not (every? #{:standalone :package-dependent :prf-hosted}
                      (vals (:notebooks config)))
      (fail! "Every runnable notebook must have a supported dependency classification."))
    (when-let [missing (seq (registry-source-errors repo-root registry))]
      (fail! (str "Notebook registry paths are missing: " (pr-str missing))))
    (when-not (= expected actual)
      (fail! (str "Pinned Git revisions diverge between data/notebook-runtime.edn and deps.edn: "
                  (pr-str {:runtime expected :deps-edn actual}))))
    (when (.startsWith cache-root repo-root)
      (fail! (str "Managed cache must be outside tracked sources: " cache-root)))))

(defn managed-path [key]
  (.resolve cache-root (str (name key) "/" (get-in config [:packages key :git/sha]))))
(defn host-path []
  (.resolve cache-root (str "prf-host/" (get-in config [:prf-host :git/sha]))))
(defn host-deps-path []
  (.resolve cache-root (str "prf-host-runtime/v1/" (get-in config [:prf-host :git/sha]))))
(defn standalone-ready-path []
  (.resolve cache-root "ready/standalone"))

(declare sha256-file)

(defn- host-local-roots [^Path host-root source-config]
  (->> (:deps source-config)
       vals
       (keep :local/root)
       (map (fn [root]
              (let [path (Paths/get (str root) (make-array String 0))
                    absolute (if (.isAbsolute path) path (.resolve host-root path))
                    absolute (.normalize absolute)]
                (when-not (.startsWith absolute host-root)
                  (throw (ex-info "PRF host local dependency escapes its pinned checkout"
                                  {:local/root root :host/root (str host-root)})))
                (str (.relativize host-root absolute)))))
       distinct
       vec))

(defn host-dependency-config [^Path host-root source-config]
  (let [paths (->> (concat (:paths source-config) ["protocols_src"])
                   (remove #{"."})
                   distinct
                   vec)
        deps (into {}
                   (map (fn [[coordinate dependency]]
                          [coordinate
                           (if-let [local-root (:local/root dependency)]
                             (assoc dependency :local/root
                                    (let [path (Paths/get (str local-root) (make-array String 0))
                                          absolute (if (.isAbsolute path) path (.resolve host-root path))]
                                      (str (.relativize host-root (.normalize absolute)))))
                             dependency)]))
                   (:deps source-config))]
    (cond-> {:paths paths :deps deps}
      (:mvn/repos source-config) (assoc :mvn/repos (:mvn/repos source-config)))))

(defn- managed-source-fingerprint [^Path root relative-dirs]
  (let [files (->> relative-dirs
                   (mapcat (fn [relative-dir]
                             (let [dir (.resolve root relative-dir)]
                               (when-not (Files/isDirectory dir (make-array LinkOption 0))
                                 (throw (ex-info "Pinned PRF source directory is missing"
                                                 {:directory (str dir)})))
                               (filter #(.isFile ^java.io.File %)
                                       (file-seq (.toFile dir))))))
                   (map (fn [^java.io.File file]
                          [(str/replace (str (.relativize root (.toPath file))) "\\" "/")
                           (sha256-file (.toPath file))]))
                   (sort-by first)
                   vec)
        digest (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and 0xff %))
                    (.digest digest (.getBytes (pr-str files) "UTF-8"))))))

(defn- copy-host-source! [^Path host-root ^Path runtime-root relative-dir]
  (let [source (.resolve host-root relative-dir)
        target (.resolve runtime-root relative-dir)]
    (when-not (Files/isDirectory source (make-array LinkOption 0))
      (throw (ex-info "Pinned PRF source directory is missing" {:directory (str source)})))
    (doseq [^java.io.File file (file-seq (.toFile source))]
      (let [source-path (.toPath file)
            relative (.relativize source source-path)
            target-path (.resolve target relative)]
        (when (Files/isSymbolicLink source-path)
          (throw (ex-info "Symlinks are not allowed in the PRF source projection"
                          {:source (str source-path)})))
        (if (.isDirectory file)
          (Files/createDirectories target-path
                                   (make-array java.nio.file.attribute.FileAttribute 0))
          (do
            (Files/createDirectories (.getParent target-path)
                                     (make-array java.nio.file.attribute.FileAttribute 0))
            (Files/copy source-path target-path
                        (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))))

(defn materialize-host-dependency! []
  (let [host-root (host-path)
        source-config (edn/read-string
                       (slurp (.toFile (.resolve host-root "deps.edn"))))
        runtime-config (host-dependency-config host-root source-config)
        runtime-root (host-deps-path)
        source-paths (vec (distinct (concat (:paths runtime-config)
                                            (host-local-roots host-root source-config))))
        expected-fingerprint (managed-source-fingerprint host-root source-paths)
        fingerprint-file (.resolve runtime-root ".source-fingerprint")]
    (Files/createDirectories runtime-root
                             (make-array java.nio.file.attribute.FileAttribute 0))
    (if (Files/exists fingerprint-file (make-array LinkOption 0))
      (let [recorded (str/trim (slurp (.toFile fingerprint-file)))
            actual (managed-source-fingerprint runtime-root source-paths)
            recorded-config (edn/read-string
                             (slurp (.toFile (.resolve runtime-root "deps.edn"))))]
        (when-not (and (= expected-fingerprint recorded)
                       (= recorded actual)
                       (= runtime-config recorded-config))
          (fail! (str "Managed PRF source projection is stale or modified at " runtime-root))))
      (do
        (doseq [source-path source-paths]
          (copy-host-source! host-root runtime-root source-path))
        (let [actual (managed-source-fingerprint runtime-root source-paths)]
          (when-not (= expected-fingerprint actual)
            (fail! (str "Managed PRF source projection failed its source fingerprint check at "
                        runtime-root)))
          (spit (.toFile fingerprint-file) (str expected-fingerprint "\n"))
          (spit (.toFile (.resolve runtime-root "deps.edn")) (pr-str runtime-config)))))
    runtime-root))

(defn run-command! [args dir]
  (let [p (doto (ProcessBuilder. ^java.util.List (mapv str args))
            (.directory (.toFile ^Path dir))
            (.inheritIO))
        code (.waitFor (.start p))]
    (when-not (zero? code)
      (throw (ex-info "Command failed" {:command args :exit code})))
    code))

(defn command-output [args dir]
  (let [{:keys [exit out err]} (apply shell/sh (concat args [:dir (str dir)]))]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " args))
                      {:exit exit :stdout out :stderr err})))
    (str/trim out)))

(defn ensure-checkout! [git-url sha dest]
  (Files/createDirectories (.getParent ^Path dest)
                           (make-array java.nio.file.attribute.FileAttribute 0))
  (if (Files/exists dest (make-array LinkOption 0))
    (let [actual (command-output ["git" "rev-parse" "HEAD"] dest)
          remote (command-output ["git" "config" "--get" "remote.origin.url"] dest)
          changes (command-output ["git" "status" "--porcelain"] dest)]
      (when-not (= sha actual)
        (fail! (str "Managed checkout at " dest " is " actual ", expected " sha)))
      (when-not (= git-url remote)
        (fail! (str "Managed checkout origin at " dest " is " remote ", expected " git-url)))
      (when-not (empty? changes)
        (fail! (str "Managed checkout at " dest " has uncommitted changes; refusing evaluation."))))
    (do
      (run-command! ["git" "clone" "--no-checkout" "--filter=blob:none" git-url (str dest)] repo-root)
      (run-command! ["git" "-C" (str dest) "fetch" "--depth=1" "origin" sha] repo-root)
      (run-command! ["git" "-C" (str dest) "checkout" "--detach" sha] repo-root)))
  (let [actual (command-output ["git" "rev-parse" "HEAD"] dest)]
    (when-not (= sha actual)
      (fail! (str "Checkout verification failed at " dest ": " actual " != " sha))))
  dest)

(declare prefetch-dependencies!)

(defn setup! [flags]
  (validate-config!)
  (when (or (:prf-host flags) (:all flags))
    (ensure-checkout! (get-in config [:prf-host :git/url])
                      (get-in config [:prf-host :git/sha]) (host-path))
    (materialize-host-dependency!))
  (when (or (:packages flags) (:all flags))
    (doseq [[key package] (:packages config)]
      (ensure-checkout! (:git/url package) (:git/sha package) (managed-path key))))
  (prefetch-dependencies! {:prf-host (or (:prf-host flags) (:all flags))
                           :packages (or (:packages flags) (:all flags))})
  (when (or (:standalone flags) (:all flags))
    (Files/createDirectories (.resolve cache-root "ready")
                             (make-array java.nio.file.attribute.FileAttribute 0))
    (spit (.toFile (standalone-ready-path)) "standalone dependency closure resolved\n"))
  (println "Managed source setup complete."))

(defn print-list! []
  (validate-config!)
  (doseq [[id entry] (sort-by (comp name key) by-id)]
    (println (format "%-42s %-18s %s" (name id)
                     (name (get-in config [:notebooks id])) (:path entry)))))

(defn parse-flags [args]
  (loop [remaining args out {}]
    (if-let [arg (first remaining)]
      (case arg
        "--with-prf-host" (recur (rest remaining) (assoc out :prf-host true))
        "--with-packages" (recur (rest remaining) (assoc out :packages true))
        "--output" (if-let [value (second remaining)]
                     (recur (nnext remaining) (assoc out :output value))
                     (fail! "--output requires a directory"))
        (fail! (str "Unknown option: " arg)))
      out)))

(defn sha256-file [^Path path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        bytes (Files/readAllBytes path)]
    (apply str (map #(format "%02x" (bit-and 0xff %)) (.digest digest bytes)))))

(defn normalized-classpath-entry [entry]
  (let [path (Paths/get entry (make-array String 0))
        absolute (if (.isAbsolute path) path (.resolve repo-root path))
        absolute (.normalize absolute)
        gitlibs-root (Paths/get (str (System/getProperty "user.home") "/.gitlibs/libs")
                                (make-array String 0))
        m2-root (Paths/get (str (System/getProperty "user.home") "/.m2/repository")
                           (make-array String 0))]
    (cond
      (.startsWith absolute cache-root) (str "managed:" (.relativize cache-root absolute))
      (.startsWith absolute repo-root) (str "repo:" (.relativize repo-root absolute))
      (.startsWith absolute gitlibs-root) (str "gitlibs:" (.relativize gitlibs-root absolute))
      (.startsWith absolute m2-root) (str "maven:" (.relativize m2-root absolute))
      :else entry)))

(defn git-blob [^Path root file]
  (command-output ["git" "rev-parse" (str "HEAD:" file)] root))

(defn verified-fixtures! []
  (into {}
        (map (fn [[path expected]]
               (let [actual (git-blob (host-path) path)]
                 (when-not (= expected actual)
                   (fail! (str "PRF fixture identity mismatch for " path
                               ": expected " expected ", got " actual)))
                 [path actual])))
        (get-in config [:prf-host :fixture-blobs])))

(defn source-revision [^Path root]
  (try
    (command-output ["git" "rev-parse" "HEAD"] root)
    (catch Exception _
      (command-output ["jj" "-R" (str root) "log" "--no-graph" "-r" "@-" "-T" "commit_id"] root))))

(declare local-deps-sdeps)

(defn prefetch-dependencies! [flags]
  (run-command! ["clojure" "-Sdeps" (local-deps-sdeps flags) "-P"] repo-root))

(defn local-deps-sdeps [flags]
  (let [deps (cond-> {}
               (:prf-host flags)
               (assoc 'protocol-robustness/framework {:local/root (str (host-deps-path))})
               (:packages flags)
               (assoc 'prf/canonical {:local/root (str (.resolve (managed-path :canonical) "clj"))}
                      'prf/pro-rata-allocation {:local/root (str (.resolve (managed-path :allocation) "clj"))}))]
    (pr-str {:deps deps})))

(defn classpath-duplicates! [flags]
  (let [command ["clojure" "-Sdeps" (local-deps-sdeps flags) "-Spath"]
        cp (command-output command repo-root)
        path-separator (re-pattern (java.util.regex.Pattern/quote java.io.File/pathSeparator))
        paths (str/split cp path-separator)
        inventory (namespace-inventory paths)
        canonical-duplicates (duplicate-canonical-namespaces paths)
        source-conflicts (conflicting-notebook-source-copies inventory)]
    (when (seq canonical-duplicates)
      (fail! (str "Duplicate prf.canonical source namespaces on classpath: "
                  (pr-str canonical-duplicates))))
    (when (seq source-conflicts)
      (fail! (str "Divergent PRF notebook source copies on classpath: "
                  (pr-str source-conflicts))))
    cp))

(defn build-command [id flags output]
  (let [entry (get by-id id)
        source (.toAbsolutePath (.resolve repo-root (:path entry)))
        code (str "(require '[nextjournal.clerk :as clerk]) "
                  "(clerk/build! {:paths [\"" source "\"] :out-path \"" output "\"}) "
                  "(shutdown-agents) (System/exit 0)")
        main "-M"]
    ["clojure" "-Sdeps" (local-deps-sdeps flags)
     (str "-J-Duser.dir=" (:resource-root flags)) main "-e" code]))

(defn verify-output! [id out-root]
  (let [files (->> (file-seq (.toFile ^Path out-root))
                   (filter #(and (.isFile %)
                                 (or (.endsWith (.getName %) ".html")
                                     (.endsWith (.getName %) ".edn")))))
        expected-markers (case id
                           :hardening-artifact ["HARDENING EVIDENCE"]
                           :portable-canonical-package ["Portable canonical package example"]
                           :report ["Evidence Control Panel" "s04-dispute-timeout-autocancel"]
                           :canonical-framing ["Consecutive Concatenation, Made Visible"]
                           [])
        expected-root (get-in config [:qualification-roots id])
        html-files (filter #(.endsWith (.getName %) ".html") files)
        edn-files (filter #(.endsWith (.getName %) ".edn") files)
        content (str/join "\n" (map slurp files))]
    (when (or (empty? html-files) (empty? edn-files))
      (fail! (str "Clerk build produced no HTML files under " out-root)))
    (doseq [expected expected-markers]
      (when-not (str/includes? content expected)
        (fail! (str "Built notebook output did not contain expected marker: " expected))))
    (when (and expected-root (not (str/includes? content expected-root)))
      (fail! (str "Built canonical output root differs from pinned expected value: " expected-root)))
    {:html-files (mapv #(.getName %) html-files)
     :edn-output-files (mapv #(.getName %) edn-files)
     :expected-markers expected-markers
     :markers-found? (every? #(str/includes? content %) expected-markers)
     :canonical/example-root expected-root
     :sha256 (into {} (map (fn [f] [(.getName f) (sha256-file (.toPath f))]) files))}))

(defn run-notebook! [id flags]
  (validate-config!)
  (let [entry (or (get by-id id) (fail! (str "Unknown notebook id: " id)))
        category (get-in config [:notebooks id])]
    (when (= category :prf-hosted)
      (when-not (:prf-host flags)
        (fail! "This notebook is PRF-hosted; opt in with --with-prf-host."))
      (ensure-checkout! (get-in config [:prf-host :git/url])
                        (get-in config [:prf-host :git/sha]) (host-path))
      (materialize-host-dependency!))
    (when (= category :package-dependent)
      (when-not (:packages flags)
        (fail! "This notebook needs extracted packages; opt in with --with-packages."))
      (doseq [[key package] (:packages config)]
        (ensure-checkout! (:git/url package) (:git/sha package) (managed-path key))))
    (when (and (not= category :prf-hosted) (:prf-host flags))
      (fail! "--with-prf-host is only valid for PRF-hosted notebooks."))
    (when (and (not= category :package-dependent) (:packages flags))
      (fail! "--with-packages is only valid for package-dependent notebooks."))
    (let [resource-root (if (= category :prf-hosted) (host-path) repo-root)
          output (or (:output flags)
                     (str (.resolve repo-root (str ".cache/notebooks/" (name id)))))
          out-root (-> (Paths/get output (make-array String 0)) .toAbsolutePath .normalize)
          flags (assoc flags :resource-root resource-root)
          _ (if (or (:prf-host flags) (:packages flags))
              (prefetch-dependencies! flags)
              (when-not (Files/exists (standalone-ready-path) (make-array LinkOption 0))
                (fail! "Run setup --standalone before notebook evaluation; setup resolves dependencies explicitly.")))
          classpath (classpath-duplicates! flags)
          command (build-command id flags out-root)]
      (run-command! command repo-root)
      (let [result (verify-output! id out-root)
            record {:notebook/id id
                    :notebook/class category
                    :notebook/path (:path entry)
                    :source/revision (source-revision repo-root)
                    :prf-host/revision (when (= category :prf-hosted)
                                         (command-output ["git" "rev-parse" "HEAD"] (host-path)))
                    :resource/root (normalized-classpath-entry (str resource-root))
                    :resource/root-source-revision (when (= category :prf-hosted)
                                                     (command-output ["git" "rev-parse" "HEAD"]
                                                                     (host-path)))
                    :fixture/identities (when (= category :prf-hosted)
                                          (verified-fixtures!))
                    :evaluation/status :passed
                    :evaluation/output result
                    :dependency/source-pins
                    (cond-> {}
                      (:prf-host flags)
                      (assoc :prf-host (get-in config [:prf-host :git/sha]))
                      (:packages flags)
                      (assoc :canonical (get-in config [:packages :canonical :git/sha])
                             :allocation (get-in config [:packages :allocation :git/sha])))
                    :namespace-check (when (:packages flags)
                                       {:prf.canonical :unique-on-classpath})
                    :dependency/classpath
                    (mapv normalized-classpath-entry
                          (str/split classpath
                                     (re-pattern (java.util.regex.Pattern/quote java.io.File/pathSeparator))))}]
        (Files/createDirectories (.resolve repo-root "qualifications")
                                 (make-array java.nio.file.attribute.FileAttribute 0))
        (spit (.toFile (.resolve repo-root (str "qualifications/" (name id) ".edn")))
              (with-out-str (pprint/pprint record)))
        (println (str "Notebook evaluation passed: " (name id)
                      "; record: qualifications/" (name id) ".edn"))))))

(defn -main [& args]
  (validate-config!)
  (let [[command & tail] args]
    (try
      (case command
        "list" (print-list!)
        "setup" (let [options (setup-options tail)]
                  (when-let [error (:error options)]
                    (fail! (str "Invalid setup options: " (pr-str options)
                                ". Use --standalone, --prf-host, --packages, or --all.")))
                  (setup! options))
        "run" (let [[id & options] tail]
                (when-not id (fail! "Use run <notebook-id> [--with-prf-host] [--with-packages]."))
                (run-notebook! (keyword id) (parse-flags options)))
      (fail! "Usage: prf-notebooks list | setup --standalone|--prf-host|--packages|--all | run <id> [opt-ins]"))
      (finally (shutdown-agents)))))
