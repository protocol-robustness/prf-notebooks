;; # Use-Case Mappings — Stakeholder Terms to Protocol Concepts
;;
;; **Question:** What does a use-case mapping tell us about the relationship
;; between an adopter's language and PRF's protocol vocabulary?
;;
;; This notebook loads one external use-case registry explicitly and inspects
;; its declared mappings. It focuses on the appeal-escalation example because
;; the mappings include direct correspondences and clearly explained
;; approximations. The example is illustrative and is not framework-owned.

^{:nextjournal.clerk/toc true
  :nextjournal.clerk/dark-mode true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.use-case-mappings
  (:require [nextjournal.clerk :as clerk]
            [resolver-sim.use-cases.registry :as use-cases]))

;; ## 1. Load the adopter's registry
;;
;; Use-case bundles are loaded by an explicit path. Loading validates the
;; registry and produces roots for both the registry and each definition.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def loaded
  (use-cases/load-use-case-registry "examples/use-cases/ecommerce/registry.edn"))

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(def selected-use-case
  (get (use-cases/use-case-index loaded) :dispute/appeal-escalation))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:item "Use-case" :value (:concept/name selected-use-case)}
  {:item "Use-case ID" :value (:concept/id selected-use-case)}
  {:item "Registry source" :value (:use-case-registry/source loaded)}
  {:item "Registry root" :value (:use-case-registry/root loaded)}
  {:item "Definition root" :value (use-cases/definition-root selected-use-case)}])

;; ## 2. Read the mapping as a semantic crosswalk
;;
;; A mapping connects a stakeholder term to one or more protocol concepts.
;; Confidence is shown only when the use-case author declared it. A missing
;; confidence label is not upgraded to an exact or proven equivalence.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def mapping-surfaces
  [[:role :concept/roles]
   [:entity :concept/entities]
   [:action :concept/actions]
   [:outcome :concept/outcomes]])

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def mapping-rows
  (->> mapping-surfaces
       (mapcat (fn [[surface field]]
                 (for [[term mapping] (get selected-use-case field)]
                   {:surface surface
                    :stakeholder-term term
                    :protocol-concepts (let [targets (:maps-to mapping)]
                                         (if (seq targets) (vec (sort-by str targets)) [:unmapped]))
                    :confidence (or (:mapping/confidence mapping) :not-stated)
                    :note (:mapping/note mapping)})))
       (sort-by (juxt :surface :stakeholder-term))
       vec))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table mapping-rows)

;; ### Read a few rows closely
;;
;; - `:appellant` maps to both sender and recipient because either escrow party
;;   may appeal. The mapping is explicitly approximate.
;; - `:appeal-deadline` maps to the protocol deadline concept, but the protocol
;;   derives deadlines from timestamps rather than storing a separate entity.
;; - `:forfeit-bond` maps to settlement: forfeiture is a fund-flow result, not a
;;   distinct protocol action.
;;
;; The notes preserve why a target is useful while showing where the concepts
;; do not line up one-to-one.

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(def mapping-counts (frequencies (map :confidence mapping-rows)))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (mapv (fn [[confidence count]] {:confidence confidence :mapping-count count})
       (sort-by key mapping-counts)))

;; ## 3. Keep mappings separate from execution and authority
;;
;; A `:maps-to` entry is a declared semantic crosswalk. By itself it does not:
;;
;; - execute the named protocol action;
;; - prove that the stakeholder and protocol concepts are equivalent;
;; - establish that the selected capability is implemented or bound to an
;;   invocation; or
;; - grant the use-case owner signing, execution, verification, or adoption
;;   authority.
;;
;; Those are separate questions answered by implementation, capability
;; bindings, evidence, and any authenticated authority policy in scope.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(do
  (assert selected-use-case "The declared appeal-escalation example must load")
  (assert (some #(and (= :role (:surface %))
                      (= :appellant (:stakeholder-term %))
                      (= :approximate (:confidence %)))
                mapping-rows)
          "The appellant mapping and its declared approximation should remain visible")
  (assert (some #(and (= :entity (:surface %))
                      (= :appeal-deadline (:stakeholder-term %))
                      (some #{:protocol.entity/deadline} (:protocol-concepts %)))
                mapping-rows)
          "The appeal deadline should map to the protocol deadline concept")
  true)
