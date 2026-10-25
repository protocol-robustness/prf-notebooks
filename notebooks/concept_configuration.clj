;; # Concepts: configuration, extension, and effect
;;
;; How does PRF turn a concept definition into useful explanations—and what
;; does adding one actually change? This notebook loads the authoritative PRF
;; registry, follows a real shortfall concept into benchmark report resolution,
;; and shows the exact files and checks involved in adding a concept.
;;
;; The boundary is important: concepts explain protocol results. They do not
;; configure protocol execution, change evidence, alter hashes, or affect scores.

^{:nextjournal.clerk/toc true
  :nextjournal.clerk/dark-mode true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.concept-configuration
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [resolver-sim.concepts.benchmark :as benchmark-concepts]
            [resolver-sim.concepts.registry :as concepts]))

;; ## 1. Load the authoritative concept registry
;;
;; This notebook reads PRF's registered definitions from the pinned PRF host
;; checkout. It does not copy those definitions into the notebook repository.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def registry-snapshot (concepts/load-registry))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def registry (:registry registry-snapshot))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def concept-definitions (:concepts registry-snapshot))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md
 (str "Loaded **" (count concept-definitions)
      "** concept definitions from registry version **"
      (:concept-registry/version registry) "**. The registry loader checks that "
      "each registered file exists, required fields are present, and concept IDs "
      "are unique. Standard mappings are normalized with an explicit mapping status."))

;; ## 2. Featured concept: Configuration
;;
;; This is a useful concept to start with because its name can be confused with
;; deployment configuration. Here it means the explicit settings under which a
;; scenario, benchmark, or claim result is valid. The definition itself says
;; production deployment configuration is out of scope.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def configuration-concept
  (concepts/lookup-concept concept-definitions :concept/configuration))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Configuration concept" "Meaning"]
  :rows [[(:concept/name configuration-concept)
          (:concept/summary configuration-concept)]
         ["Stakeholder question"
          (:concept/stakeholder-question configuration-concept)]
         ["Related concept"
          (str/join ", " (map name (:concept/related configuration-concept)))]]})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Subconcept" "What it covers"]
  :rows (mapv (fn [{:concept/keys [id name summary]}]
                [(str id " — " name) summary])
              (:concept/subconcepts configuration-concept))})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md
 (str "The definition identifies failure modes such as **"
      (str/join "**, **" (map :failure/name (:concept/failure-modes configuration-concept)))
      "**. It helps a reviewer ask whether settings were explicit and comparable. "
      "It does not define a canonical production deployment configuration, and it "
      "does not itself configure a protocol."))

;; ## 3. Registry catalog
;;
;; This compact catalog makes the other available concepts discoverable. The
;; category walkthroughs below highlight selected groups in more detail.
^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Concept ID" "Name" "Type" "Layer" "Protocols"]
  :rows (->> concept-definitions
             (sort-by (comp str :concept/id))
             (mapv (fn [concept]
                     [(str (:concept/id concept))
                      (:concept/name concept)
                      (name (:concept/type concept))
                      (name (:concept/layer concept))
                      (str/join ", " (map name (sort (:concept/protocols concept))))])))} )

;; ## 4. Allocation example: Shortfall
;;
;; `:allocation/shortfall` is a real registered concept. Its wording tells a
;; reader what a detected deficit means; its mappings connect that wording to
;; protocol-facing labels. Those labels are not runtime types or capabilities.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def shortfall-concept
  (concepts/lookup-concept concept-definitions :allocation/shortfall))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Field" "Registered value"]
  :rows [["ID" (:concept/id shortfall-concept)]
         ["Name" (:concept/name shortfall-concept)]
         ["Stakeholder question" (:concept/stakeholder-question shortfall-concept)]
         ["Summary" (:concept/summary shortfall-concept)]
         ["Applicable protocols" (str/join ", " (map name (sort (:concept/protocols shortfall-concept))))]
         ["Related concepts" (str/join ", " (map name (sort (:concept/related shortfall-concept))))]]})

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def shortfall-mappings
  (->> [:concept/roles :concept/entities :concept/actions :concept/outcomes]
       (mapcat (fn [category]
                 (for [[mapping-id mapping] (get shortfall-concept category)]
                   [(name category) (name mapping-id)
                    (str/join ", " (map name (:maps-to mapping)))
                    (name (concepts/mapping-status mapping))])))
       (sort-by (juxt first second))
       vec))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Definition section" "Concept item" "Protocol labels" "Mapping status"]
  :rows shortfall-mappings})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md "A mapping is an explanatory link. `:native`, `:derived`, `:approximate`, and `:not-modelled` describe the declared mapping quality; none is a protocol validation result.")

;; ## 5. Consensus and decision quality
;;
;; Consensus concepts describe questions about collective decisions. They are
;; separate entries, so a report can explain who had authority, what evidence
;; supported the decision, and when the result became final without treating
;; those questions as interchangeable.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def consensus-examples
  (mapv (fn [concept-id]
          (concepts/lookup-concept concept-definitions concept-id))
        [:consensus/authority :consensus/evidence :consensus/finality
         :consensus/escalation :consensus/liveness :consensus/legitimacy]))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Concept" "Question it explains" "Example failure mode"]
  :rows (mapv (fn [concept]
                [(:concept/name concept)
                 (:concept/stakeholder-question concept)
                 (or (:failure/name (first (:concept/failure-modes concept)))
                     (some-> concept :concept/summary))])
              consensus-examples)})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md "These are explanatory lenses, not a consensus algorithm or authority grant. A registered concept cannot appoint a decision-maker, make evidence sufficient, or finalize an on-chain outcome.")

;; ## 6. Protocol mechanism related to contracts
;;
;; The registry does not currently define a `:contract` concept type. The
;; closest useful example is `:mechanism/pro-rata-allocation`: a protocol-layer
;; description that names properties and their stated enforcement surfaces.
;; It explains what the mechanism is intended to guarantee; it does not prove
;; that a particular deployed contract has those properties.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def pro-rata-mechanism
  (concepts/lookup-concept concept-definitions :mechanism/pro-rata-allocation))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Declared property" "Stated enforcement surface"]
  :rows (mapv (fn [{:keys [property enforcement]}]
                [(name property) (name enforcement)])
              (:concept/guarantees pro-rata-mechanism))})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Mechanism description" "Explicitly out of scope"]
  :rows [[(:concept/summary pro-rata-mechanism)
          (str/join " " (:concept/out-of-scope pro-rata-mechanism))]]})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md "To make a claim about a deployed contract, inspect that deployment's source/artifact identity, runtime code, configuration, execution trace, and relevant tests or proofs. This concept entry is a guide to the mechanism and its stated boundaries—not contract verification.")

;; ## 7. More concepts, grouped by topic
;;
;; These tables use the registered IDs and summaries. They are examples of
;; what the concept vocabulary can describe, not attestations that a protocol
;; implements or satisfies each concept.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(defn concept-overview-rows [concept-ids]
  (mapv (fn [concept-id]
          (let [concept (concepts/lookup-concept concept-definitions concept-id)]
            [(str concept-id)
             (:concept/name concept)
             (or (:concept/summary concept)
                 (:concept/stakeholder-question concept))]))
        concept-ids))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Allocation and claims" "Name" "What the concept describes"]
  :rows (concept-overview-rows
         [:allocation/partial-fill :allocation/pro-rata-fairness
          :allocation/redistribution-fairness :allocation/deferred-claim
          :allocation/slash-obligation :allocation/slash-distribution
          :allocation/bounty-payable :allocation/bounty-payable-reserve
          :allocation/deferred-class :allocation/fractional-remainder])})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Assurance and forensic evidence" "Name" "What the concept describes"]
  :rows (concept-overview-rows
         [:verifiable-assurance/forensic-confidence :attestation/verification
          :forensic/evidence-chain :assurance/evidence-backed-classification
          :consensus/evidence :consensus/finality])})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Governance and committed content" "Name" "What the concept describes"]
  :rows (concept-overview-rows
         [:governance/proposed-content :governance/proposed-content-root
          :framework/content-root :framework/registry-entry-hash
          :framework/semantic-commitments :framework/creation-provenance
          :framework/source-creation])})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Research and incentives" "Name" "What the concept describes"]
  :rows (concept-overview-rows
         [:framework/research-command :framework/research-assignment
          :framework/incentive :framework/incentive-compatibility
          :framework/outside-option :framework/review-certificate
          :framework/procedure-execution-witness
          :framework/review-member-canonical-indices])})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Other protocol and system concepts" "Name" "What the concept describes"]
  :rows (concept-overview-rows
         [:decision-quality/solvency-status :yield/yield-bearing
          :concept/conservation :concept/confidence
          :security/force-authorisation :framework/held-custody
          :framework/protocol-extension-boundary :framework/incentive])})

;; ## 8. Follow a concept into benchmark report resolution
;;
;; A benchmark names the concepts it wants to display. The resolver selects a
;; benchmark-local definition when available, otherwise a global definition,
;; and marks unknown IDs as missing. This enriches report language; it does not
;; feed back into the benchmark's execution or score.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def example-resolution
  (benchmark-concepts/resolve-benchmark-concepts
   [:allocation/shortfall :concept/configuration :example/not-registered]))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Requested concept" "Source" "Resolved title"]
  :rows (mapv (fn [{:keys [concept/id concept/source concept]}]
                [id source (or (:concept/title concept) (:concept/name concept) "—")])
              (:resolved-entries example-resolution))})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md
 (str "The example's unknown ID remains explicitly unresolved: `"
      (pr-str (:unknown-concept-ids example-resolution))
      "`. Resolution does not invent a definition or change the underlying result."))

;; ## 9. Add a concept
;;
;; A framework concept is normally added in two coordinated places: a complete
;; definition under `data/concepts/<type>/`, and an index entry in
;; `data/concepts/registry.edn`. Keep the ID, type, layer, path, name, and
;; protocol scope consistent. The following is a schema-shaped teaching
;; template; replace the placeholder values with reviewed content before use.

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(clerk/md "```clojure\n;; data/concepts/allocation/new_concept.edn\n{:concept/id :allocation/replace-with-reviewed-id\n :concept/type :allocation\n :concept/layer :stakeholder\n :concept/name \"Reviewed stakeholder-facing name\"\n :concept/summary \"Explain the protocol outcome in plain language.\"\n :concept/stakeholder-question \"What should a stakeholder be able to understand?\"\n :concept/protocols #{:protocol/prf}\n :concept/roles {}\n :concept/entities {}\n :concept/actions {}\n :concept/outcomes {}\n :concept/failure-modes []\n :concept/metrics {}\n :concept/assumptions []\n :concept/out-of-scope []\n :concept/related []}\n```\n\nThen add a matching entry to `data/concepts/registry.edn`:\n\n```clojure\n{:concept/id :allocation/replace-with-reviewed-id\n :concept/type :allocation\n :concept/layer :stakeholder\n :concept/file \"data/concepts/allocation/new_concept.edn\"\n :concept/name \"Reviewed stakeholder-facing name\"\n :concept/status :draft\n :concept/protocols #{:protocol/prf}}\n```\n\nUse a new qualified ID. Related references should point to registered concepts. Mapping labels should be described honestly; approximate or absent mappings must not be presented as native protocol capabilities.")

;; ## 10. Validate and use the new definition
;;
;; Run `bb concepts-check` (or `make concepts-check`) from a PRF checkout. It
;; checks files, IDs, type-directory placement, required fields, and references.
;; If the concept is used by a benchmark, also add its ID to that benchmark's
;; manifest and validate the benchmark. Benchmark-local overlays belong under
;; `benchmarks/concepts/`; shadowing a global ID requires an explicit
;; `:concept/shadows-global? true` declaration.

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 {:head ["Adding a concept does" "Adding a concept does not"]
  :rows [["Make a registered explanation available to report consumers"
          "Change scenario transitions, protocol rules, or capital flows"]
         ["Allow benchmark reports to resolve a declared concept ID"
          "Change evidence capture, canonical hashes, validity, or scoring"]
         ["Expose stakeholder language, mappings, assumptions, and limitations"
          "Prove that a mapping is true or that a protocol supports the described property"]]})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/md "A concept's presence is not evidence of protocol support. Establish support separately with executable scenarios, validated evidence, benchmark coverage, and the relevant deployment configuration. Conceptual maturity and measured benchmark evidence are separate questions.")

;; ## Reproduce
;;
;; From the PRF integration checkout, run:
;;
;; ```sh
;; bin/prf-notebooks run concept-configuration --with-prf-host
;; bb concepts-check
;; ```
;;
;; This notebook is a view over the pinned PRF concept APIs and data. It writes
;; no concept files and does not change the authoritative registry.
