;; # Reachability and Ratification — What Is Connected, and What Was Adopted?
;;
;; **Audience:** Researchers, reviewers, auditors, protocol implementers.
;;
;; This executable example builds a tiny evidence-to-subject chain, has a
;; reviewer accept the subject, and has a distinct ratifier adopt that exact
;; accepted subject. It uses PRF's authenticated `subject-decisions.v2` API and
;; evidence-DAG reachability queries. All keys are public test vectors.
;;
;; Reachability answers whether a path exists in a declared graph. Authenticated
;; ratification separately verifies the subject, policy, ordered history and
;; signatures. This is content adoption, not capital-execution authority or a
;; multi-researcher threshold-voting protocol.

^{:nextjournal.clerk/toc true
  :nextjournal.clerk/dark-mode true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.reachability-and-ratification
  (:require [nextjournal.clerk :as clerk]
            [resolver-sim.assurance.subject-decisions :as decisions]
            [resolver-sim.evidence.reachability :as reachability])
  (:import [java.security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [java.util Base64 HexFormat]))

;; ## 1. Define the exact basis and local policy
;;
;; The policy pins separate reviewer and ratifier public keys, a verifier
;; identity, and a narrow scope. The subject root includes the evidence root.
;; The key seeds below are well-known test vectors and have no authority value.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(defn hex-bytes [value]
  (.parseHex (HexFormat/of) value))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(defn test-key [seed public-key]
  (let [factory (KeyFactory/getInstance "Ed25519")
        private-der (byte-array (concat (seq (hex-bytes "302e020100300506032b657004220420"))
                                        (seq (hex-bytes seed))) )
        public-der (byte-array (concat (seq (hex-bytes "302a300506032b6570032100"))
                                       (seq (hex-bytes public-key))))
        public (.generatePublic factory (X509EncodedKeySpec. public-der))]
    {:private (.generatePrivate factory (PKCS8EncodedKeySpec. private-der))
     :public-pem (str "-----BEGIN PUBLIC KEY-----\n"
                      (.encodeToString (Base64/getEncoder) (.getEncoded public))
                      "\n-----END PUBLIC KEY-----\n")}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def reviewer-key
  (test-key "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def ratifier-key
  (test-key "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
            "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def validation-root
  (decisions/root "demo-research-verifier.v1"
                   {:method :pinned-local-example
                    :checks [:basis-identity :evidence-root-present]}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def review-scope "Local illustrative research basis; test keys only.")

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def review-policy
  (decisions/policy
   {:domain :demo/research
    :purpose :demo/content-adoption
    :scope review-scope
    :subject-schema "demo-research-basis.v1"
    :validation-root validation-root
    :reviewer {:id "test-reviewer" :public-key (:public-pem reviewer-key)}
    :ratifier {:id "test-ratifier" :public-key (:public-pem ratifier-key)}}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def evidence-root
  (decisions/root "demo-research-evidence.v1"
                  {:experiment "bounded-example"
                   :result {:observed 7 :expected 7}
                   :source "notebook-defined-test-data"}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def research-basis
  (decisions/rooted "demo-research-basis.v1"
                    {:domain (:domain review-policy)
                     :purpose (:purpose review-policy)
                     :scope review-scope
                     :claim "The bounded example reproduces its stated result."
                     :evidence/root evidence-root
                     :predecessor/root nil}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def research-verifier
  (with-meta
    (fn [subject]
      {:verified? (= evidence-root (:evidence/root subject))
       :eligible? (= evidence-root (:evidence/root subject))
       :scope review-scope
       :evidence/root (:evidence/root subject)})
    {:validation/root validation-root}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def subjects {(:artifact/root research-basis) research-basis})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:artifact "Evidence" :root evidence-root}
  {:artifact "Research basis" :root (:artifact/root research-basis)}
  {:artifact "Policy" :root (:artifact/root review-policy)}
  {:artifact "Validation method" :root validation-root}])

;; ## 2. Reachability follows declared artifact references
;;
;; Each edge below corresponds to an explicit root binding: evidence is named by
;; the subject, the subject is named by both decisions, the policy is named by
;; each decision, and ratification names the exact acceptance record.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def initial-state (decisions/initial-state review-policy))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def unsigned-accept
  (decisions/decision review-policy initial-state research-verifier research-basis :accept))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def accept-event
  (decisions/sign-decision unsigned-accept (:private reviewer-key)))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def accepted-history [accept-event])

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def accepted-state
  (decisions/reconstruct review-policy research-verifier subjects accepted-history))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def unsigned-ratification
  (decisions/decision review-policy accepted-state research-verifier research-basis :ratify))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def ratification-event
  (decisions/sign-decision unsigned-ratification (:private ratifier-key)))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def complete-history (conj accepted-history ratification-event))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def reconstructed-state
  (decisions/reconstruct review-policy research-verifier subjects complete-history))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def ratification-root (:artifact/root ratification-event))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def unrelated-root
  (decisions/root "demo-unrelated-artifact.v1" {:example true}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def evidence-graph
  {:dag/nodes [{:node/id evidence-root :label "Evidence"}
               {:node/id (:artifact/root research-basis) :label "Research basis"}
               {:node/id (:artifact/root review-policy) :label "Policy"}
               {:node/id (:artifact/root accept-event) :label "Reviewer acceptance"}
               {:node/id ratification-root :label "Ratifier adoption"}
               {:node/id unrelated-root :label "Unrelated artifact"}]
   :dag/edges [{:edge/from evidence-root :edge/to (:artifact/root research-basis)}
               {:edge/from (:artifact/root research-basis) :edge/to (:artifact/root accept-event)}
               {:edge/from (:artifact/root review-policy) :edge/to (:artifact/root accept-event)}
               {:edge/from (:artifact/root research-basis) :edge/to ratification-root}
               {:edge/from (:artifact/root review-policy) :edge/to ratification-root}
               {:edge/from (:artifact/root accept-event) :edge/to ratification-root}]})

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(def reachability-checks
  {:graph-valid? (empty? (reachability/dag-structural-errors evidence-graph))
   :evidence-reaches-ratification?
   (reachability/dag-reachable? evidence-graph evidence-root ratification-root)
   :basis-reaches-ratification?
   (reachability/dag-reachable? evidence-graph (:artifact/root research-basis) ratification-root)
   :unrelated-artifact-reaches-ratification?
   (reachability/dag-reachable? evidence-graph unrelated-root ratification-root)
   :shortest-evidence-path
   (reachability/dag-shortest-path evidence-graph evidence-root ratification-root)})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (let [labels (into {} (map (juxt :node/id :label) (:dag/nodes evidence-graph)))]
   [{:check "DAG structure is valid" :result (:graph-valid? reachability-checks)}
    {:check "Evidence reaches ratification record"
     :result (:evidence-reaches-ratification? reachability-checks)}
    {:check "Exact basis reaches ratification record"
     :result (:basis-reaches-ratification? reachability-checks)}
    {:check "Unrelated artifact reaches ratification record"
     :result (:unrelated-artifact-reaches-ratification? reachability-checks)}
    {:check "Shortest evidence path"
     :result (mapv labels (:shortest-evidence-path reachability-checks))}]))

;; Reachability shows a connected path, not whether that path is trustworthy.
;; The following replay checks the signed records and recomputes the exact
;; authoritative subject from the pinned local policy and verifier.

;; ## 3. Ratification is a separate authenticated transition

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:check "Reviewer signature verifies in reconstructed history"
   :result (= :accept (get-in accepted-state [:decisions (:artifact/root research-basis) :decision]))}
  {:check "Ratifier is distinct from reviewer"
   :result (not= (:authority/id accept-event) (:authority/id ratification-event))}
  {:check "Ratification binds exact acceptance root"
   :result (= (:artifact/root accept-event) (:acceptance/root ratification-event))}
  {:check "Reconstructed authority names the same basis"
   :result (= (:artifact/root research-basis)
              (get-in reconstructed-state [:authoritative :subject/root]))}
  {:check "Policy effects are content adoption only"
   :result (= :content-adoption-only (:effects review-policy))}])

;; A valid reachability path without an authenticated ratification is not an
;; adoption. Try to ratify the same basis before it has been accepted:

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def premature-ratification
  (decisions/sign-decision
   (decisions/decision review-policy initial-state research-verifier research-basis :ratify)
   (:private ratifier-key)))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(def fail-closed-example
  (try
    (decisions/reconstruct review-policy research-verifier subjects [premature-ratification])
    {:rejected? false}
    (catch clojure.lang.ExceptionInfo error
      {:rejected? true :reason (-> error ex-data :reason)})))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(do
  (assert (:graph-valid? reachability-checks) "example DAG must be structurally valid")
  (assert (:evidence-reaches-ratification? reachability-checks)
          "evidence should reach the ratification record")
  (assert (not (:unrelated-artifact-reaches-ratification? reachability-checks))
          "unrelated artifact must remain disconnected")
  (assert (= (:artifact/root research-basis)
             (get-in reconstructed-state [:authoritative :subject/root]))
          "ratification must adopt the exact accepted subject")
  (assert (and (:rejected? fail-closed-example)
               (= :acceptance-required (:reason fail-closed-example)))
          "ratification without acceptance must fail closed")
  true)

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:case "Ratification without prior acceptance"
   :rejected? (:rejected? fail-closed-example)
   :reason (:reason fail-closed-example)}])

;; ## Interpretation and limits
;;
;; - Reachability is a graph query over declared edges. It can find a path or a
;;   disconnected artifact; it cannot validate signatures or determine policy.
;; - The ratification replay verifies reviewer/ratifier role separation,
;;   signatures, policy binding, predecessor order, and exact accepted subject.
;; - This is the stable generic `subject-decisions.v2` content-adoption flow. It
;;   is not the separate multi-researcher consensus/ratification policy, and it
;;   grants no capital, dispute-resolution, or operational authority.
;; - The Ed25519 private keys are published test vectors. Never reuse them.
