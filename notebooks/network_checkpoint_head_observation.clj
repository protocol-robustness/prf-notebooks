;; # Network Checkpoint Walkthrough — Observe a Conflicting Head
;;
;; This guided walkthrough runs the existing `prf-network-dag-learning`
;; checkpoint demo and inspects the exact evidence bundle it writes. It does
;; not reimplement checkpoint construction, signing, reconstruction, or PRF's
;; head verifier.
;;
;; The example starts two loopback source processes on one host. Each source
;; reports a different signed successor to the same pinned genesis. Both local
;; verifier processes certify their branch; PRF then classifies the observed
;; heads as conflicting. The example is not an independently operated network,
;; a Byzantine-safe quorum, or proof of global latestness.

^{:nextjournal.clerk/toc true
  :nextjournal.clerk/dark-mode true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.network-checkpoint-head-observation
  (:require [clojure.edn :as edn]
            [nextjournal.clerk :as clerk])
  (:import [java.nio.file Files LinkOption Paths]))

;; ## 1. Run the existing checkpoint exercise
;;
;; Requirements: Java 17+, Clojure CLI, the `prf-network-dag-learning` checkout,
;; and a local PRF source checkout that contains the PRO-93/94 checkpoint API.
;; The learning repo's published dependency pin predates that API, so the
;; command overrides it with the local PRF checkout. The demo refuses to
;; overwrite an existing evidence file; the timestamp and shell PID make each
;; suggested output path distinct.
;;
;; From a terminal, set these paths, then run:
;;
;; ```sh
;; export PRF_NETWORK_DAG_ROOT=/path/to/prf-network-dag-learning
;; export PRF_CHECKPOINT_SOURCE_ROOT=/path/to/protocol-robustness-framework
;; # If the PRF checkout uses jj:
;; export PRF_CHECKPOINT_SOURCE_REVISION="$(jj --repository "$PRF_CHECKPOINT_SOURCE_ROOT" log -r @ --no-graph -T 'commit_id')"
;; # For a Git checkout instead, use: git -C "$PRF_CHECKPOINT_SOURCE_ROOT" rev-parse HEAD
;; export PRF_CHECKPOINT_SOURCE_DIRTY=unknown
;; export PRF_NETWORK_DEMO_EVIDENCE="$PRF_NETWORK_DAG_ROOT/target/head-observation-$(date -u +%Y%m%dT%H%M%SZ)-$$.edn"
;; mkdir -p "$PRF_NETWORK_DAG_ROOT/target"
;; cd "$PRF_NETWORK_DAG_ROOT"
;; clojure -Sdeps "{:deps {protocol-robustness/framework {:local/root \"$PRF_CHECKPOINT_SOURCE_ROOT\"}}}" -M:head-demo "$PRF_NETWORK_DEMO_EVIDENCE"
;; cd /path/to/prf-notebooks
;; PRF_NETWORK_DEMO_EVIDENCE="$PRF_NETWORK_DEMO_EVIDENCE" clojure -M:prf-host:serve
;; ```
;;
;; Keep the PRF source identity and dirty flag with the bundle. If using Git,
;; record whether the worktree is dirty and retain the exact patch. A Jujutsu
;; commit ID identifies its snapshotted tree; still retain any later changes.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def evidence-path (System/getenv "PRF_NETWORK_DEMO_EVIDENCE"))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def evidence-load
  (cond
    (not (seq evidence-path))
    {:status :not-configured}

    (not (Files/isRegularFile (Paths/get evidence-path (make-array String 0))
                              (make-array LinkOption 0)))
    {:status :file-not-found :path evidence-path}

    :else
    (try
      {:status :loaded :artifact (edn/read-string (slurp evidence-path))}
      (catch Exception error
        {:status :unreadable :path evidence-path :error (.getMessage error)}))))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (case (:status evidence-load)
   :loaded [{:item "Evidence bundle" :value "Loaded from the path configured in PRF_NETWORK_DEMO_EVIDENCE"}
            {:item "Artifact schema" :value (get-in evidence-load [:artifact :artifact/schema])}
            {:item "PRF source revision" :value (get-in evidence-load [:artifact :prf/source-revision])}
            {:item "PRF source dirty?" :value (get-in evidence-load [:artifact :prf/source-dirty?])}]
   :file-not-found [{:item "Evidence bundle" :value "Configured path not found"}
                    {:item "Path" :value (:path evidence-load)}]
   :unreadable [{:item "Evidence bundle" :value "Could not parse as EDN"}
                {:item "Path" :value (:path evidence-load)}
                {:item "Error" :value (:error evidence-load)}]
   [{:item "Evidence bundle" :value "Not loaded"}
    {:item "Next step" :value "Run the command above, then start Clerk with PRF_NETWORK_DEMO_EVIDENCE set to the resulting file path."}]))

;; ## 2. Inspect the pinned network and authority context
;;
;; These values came from the generated bundle. They are public demo identities,
;; not network secrets. The membership and policy are locally configured for
;; this run; their presence does not establish an external trust ceremony.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def artifact (:artifact evidence-load))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (if artifact
   [{:field "Network genesis root" :value (:network/genesis-root artifact)}
    {:field "Pinned genesis checkpoint" :value (get-in artifact [:trusted-genesis :checkpoint/root])}
    {:field "Policy root" :value (get-in artifact [:authority/policy :policy/root])}
    {:field "Membership root" :value (get-in artifact [:authority/membership :membership/root])}
    {:field "Witness count" :value (count (get-in artifact [:authority/membership :membership/members]))}
    {:field "Checkpoint endorsement threshold" :value (get-in artifact [:authority/policy :policy/threshold])}
    {:field "Configured observation-source threshold" :value (:observation/threshold artifact)}
    {:field "Configured fault bound" :value (get-in artifact [:authority/policy :policy/fault-bound])}
    {:field "Intersection-safety requirement" :value (boolean (get-in artifact [:authority/policy :policy/require-intersection-safety?]))}]
   [{:field "Network context" :value "Load a generated evidence bundle to inspect these values."}]))

;; ## 3. Compare the two branch certificates
;;
;; Each local verifier reconstructs a branch from the same genesis. The
;; coordinator retains both packages and their certificates; equal sequence
;; numbers do not select a winner.

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (if artifact
   (mapv (fn [package]
           {:checkpoint-root (get-in package [:checkpoint :checkpoint/root])
            :sequence (get-in package [:checkpoint :checkpoint/sequence])
            :parent-root (get-in package [:checkpoint :checkpoint/previous-root])
            :certificate-root (get-in package [:certificate :certificate/root])
            :certificate-endorsement-count (count (get-in package [:certificate :certificate/endorsements]))})
         (:checkpoint/packages artifact))
   [{:checkpoint-root "Load a generated evidence bundle first."}]))

;; ## 4. Read the independent reconstruction and head decision

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (if artifact
   (concat
    (map-indexed (fn [index result]
                   {:check (str "Fresh verifier process " (inc index))
                    :status (:status result)
                    :head-root (:checkpoint/root result)
                    :errors (:errors result)})
                 (:checkpoint/independent-process-verifications artifact))
    [{:check "Two-source observation result"
      :status (get-in artifact [:observation/evidence :verification :status])
      :valid? (get-in artifact [:observation/evidence :verification :valid?])
      :errors (get-in artifact [:observation/evidence :verification :errors])}])
   [{:check "Verification result" :status "Load a generated evidence bundle first."}]))

;; ## 5. Inspect source identities and signed observations
;;
;; The source keys are ephemeral and generated for this local run. Nonce-bound
;; signed observations can be verified under those configured keys and the
;; reconstructed histories. This is cryptographic verification within the
;; demo's local trust setup, not independent operator identity.

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (if artifact
   (mapv (fn [source process observation check]
           {:source/id (:source/id source)
            :key/id (:source/key-id source)
            :loopback-url (:url process)
            :checkpoint/root (:checkpoint/root observation)
            :checkpoint/sequence (:checkpoint/sequence observation)
            :observation/root (:observation/root observation)
            :client-nonce (:observation/client-nonce observation)
            :signed-issued-at (:observation/issued-at observation)
            :http-request-id "not emitted by this adapter"
            :server-observed-at "not recorded separately"
            :chain-included-at "none; local exercise"
            :observation/signature-verified? (:valid? check)
            :source/independence-group (:source/independence-group source)})
         (:observer/source-trust artifact)
         (:source/processes artifact)
         (get-in artifact [:observation/evidence :observations])
         (get-in artifact [:observation/evidence :verification :observations]))
   [{:source/id "Load a generated evidence bundle first."}]))

;; ## What this run establishes — and what it does not
;;
;; The result can establish that this PRF implementation reconstructed and
;; certified both branches from the configured genesis, validated the signed
;; observations under the ephemeral keys pinned for this run, and detected
;; conflicting equal-height successors under the current verifier behavior.
;;
;; It does not establish a Byzantine-safe quorum: the example's 2-of-4
;; intersection rule is intentionally below the configured safety bound. Both
;; source processes and verifier processes run on one host under one operator.
;; The example does not implement peer discovery, network-wide fork choice,
;; finality, or global latestness. No chain inclusion occurs.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(clerk/html
 [:aside {:style {:borderLeft "3px solid #ffd166" :padding "12px 16px"
                   :background "#172328" :color "#b0c8cc"}}
  [:strong {:style {:color "#ffd166"}} "Scope boundary"]
  [:p "This is a local integration exercise. The evidence file is generated by the demo, not an independently audited record. Preserve the exact PRF source revision and any local patch alongside it. A matching result is not a claim of global latestness or network consensus."]])
