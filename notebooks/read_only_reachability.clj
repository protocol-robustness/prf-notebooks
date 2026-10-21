;; # Read-only Reachability — What Can This DAG Tell Us?
;;
;; **Audience:** New PRF users, researchers, reviewers.
;;
;; This notebook makes a small directed acyclic graph and asks whether paths
;; exist between declared artifacts. It performs in-memory queries only: it
;; does not persist nodes, authenticate authors, evaluate policy, or select a
;; current network head.

^{:nextjournal.clerk/toc true
  :nextjournal.clerk/dark-mode true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.read-only-reachability
  (:require [nextjournal.clerk :as clerk]
            [resolver-sim.evidence.reachability :as reachability]))

;; ## 1. Declare a graph
;;
;; The arrows mean only that the supplied graph declares a relationship. A
;; path can be queried without deciding whether any node or edge is trusted.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def graph
  {:dag/nodes [{:node/id :question :label "Research question"}
               {:node/id :evidence :label "Evidence"}
               {:node/id :reproduction :label "Reproduction"}
               {:node/id :finding :label "Finding"}
               {:node/id :unrelated :label "Unrelated note"}]
   :dag/edges [{:edge/from :question :edge/to :evidence}
               {:edge/from :evidence :edge/to :reproduction}
               {:edge/from :reproduction :edge/to :finding}]})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (mapv (fn [{:keys [node/id label]}] {:artifact label :id id})
       (:dag/nodes graph)))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 (mapv (fn [{:keys [edge/from edge/to]}]
         {:from (get-in (first (filter #(= from (:node/id %)) (:dag/nodes graph)))
                        [:label])
          :to (get-in (first (filter #(= to (:node/id %)) (:dag/nodes graph)))
                      [:label])})
       (:dag/edges graph)))

;; ## 2. Ask read-only path questions

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(def reachability-results
  {:structure-errors (reachability/dag-structural-errors graph)
   :question-reaches-finding?
   (reachability/dag-reachable? graph :question :finding)
   :evidence-reaches-finding?
   (reachability/dag-reachable? graph :evidence :finding)
   :unrelated-reaches-finding?
   (reachability/dag-reachable? graph :unrelated :finding)
   :shortest-question-to-finding
   (reachability/dag-shortest-path graph :question :finding)})

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:query "Graph has no structural errors"
   :result (:structure-errors reachability-results)}
  {:query "Question reaches finding"
   :result (:question-reaches-finding? reachability-results)}
  {:query "Evidence reaches finding"
   :result (:evidence-reaches-finding? reachability-results)}
  {:query "Unrelated note reaches finding"
   :result (:unrelated-reaches-finding? reachability-results)}
  {:query "Shortest question-to-finding path"
   :result (:shortest-question-to-finding reachability-results)}])

;; ## 3. Change the graph and query again
;;
;; Removing one edge is enough to break the route. The query observes the new
;; graph value; it does not mutate a stored graph or rewrite an artifact.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def graph-without-evidence-to-reproduction
  (update graph :dag/edges
          (fn [edges]
            (vec (remove #(and (= :evidence (:edge/from %))
                               (= :reproduction (:edge/to %)))
                         edges)))))

^{:nextjournal.clerk/visibility {:code :show :result :show}}
(def changed-graph-results
  {:structure-errors
   (reachability/dag-structural-errors graph-without-evidence-to-reproduction)
   :question-reaches-finding?
   (reachability/dag-reachable? graph-without-evidence-to-reproduction
                                :question :finding)
   :evidence-reaches-finding?
   (reachability/dag-reachable? graph-without-evidence-to-reproduction
                                :evidence :finding)})

;; Structural validity and reachability are distinct checks. A dangling edge
;; and a cycle are rejected by the structural validator:

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def dangling-edge-graph
  (update graph :dag/edges conj {:edge/from :evidence :edge/to :missing-node}))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def cyclic-graph
  (update graph :dag/edges conj {:edge/from :finding :edge/to :question}))

^{:nextjournal.clerk/visibility {:code :fold :result :show}}
(clerk/table
 [{:case "Remove a declared connection"
   :structure-errors (:structure-errors changed-graph-results)
   :question-reaches-finding? (:question-reaches-finding? changed-graph-results)
   :evidence-reaches-finding? (:evidence-reaches-finding? changed-graph-results)}
  {:case "Edge points to an undeclared node"
   :structure-errors (reachability/dag-structural-errors dangling-edge-graph)}
  {:case "Connection creates a cycle"
   :structure-errors (reachability/dag-structural-errors cyclic-graph)}])

;; ## What this does not establish
;;
;; - Reachability is a query over the nodes and edges supplied here.
;; - It does not prove that artifacts were published, persisted, or complete.
;; - It does not authenticate a researcher or validate a signature or policy.
;; - It does not show that this is the newest graph another participant knows.
;;
;; Continue with `reachability_and_ratification.clj` to see reachability and
;; authenticated adoption as separate checks.

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(do
  (assert (empty? (:structure-errors reachability-results)))
  (assert (:question-reaches-finding? reachability-results))
  (assert (:evidence-reaches-finding? reachability-results))
  (assert (not (:unrelated-reaches-finding? reachability-results)))
  (assert (false? (:evidence-reaches-finding? changed-graph-results)))
  (assert (some #{:dag/edge-endpoint-undeclared}
                (reachability/dag-structural-errors dangling-edge-graph)))
  (assert (some #{:dag/cycle} (reachability/dag-structural-errors cyclic-graph)))
  true)
