# Learning path: evidence graphs in a researcher network

This guide connects three PRF capabilities that are easy to mistake for one
another: graph reachability, authenticated content adoption, and a shared
researcher network. The examples use APIs that exist today, then identify the
work needed to make the graph itself a network-published object.

## Start with the executable example

From the `prf-notebooks` checkout, evaluate the current example:

```bash
bin/prf-notebooks run reachability-and-ratification --with-prf-host
```

Or start the local Clerk server and open
`/notebooks/reachability_and_ratification.clj` as described in the root
[README](../../README.md).

The notebook creates content roots for evidence, a research basis, policy,
acceptance, and ratification. It asks whether an explicit directed path links
the evidence to the ratification. It then separately reconstructs and verifies
the signed acceptance and ratification history. The example uses public test
keys and notebook-local data; it does not connect to a researcher service.

## Example 1: reachability is a graph query

The notebook builds a graph like this:

```clojure
{:dag/nodes [{:node/id evidence-root}
             {:node/id basis-root}
             {:node/id acceptance-root}
             {:node/id ratification-root}]
 :dag/edges [{:edge/from evidence-root :edge/to basis-root}
             {:edge/from basis-root :edge/to acceptance-root}
             {:edge/from acceptance-root :edge/to ratification-root}]}
```

Then it asks:

```clojure
(reachability/dag-reachable? graph evidence-root ratification-root)
```

This proves that the supplied graph declares a path. It does not prove that the
nodes were published, that their contents match the roots, or that the path was
authorized. The implementation checks DAG structure separately; see PRF's
[reachability tests](https://github.com/protocol-robustness/protocol-robustness-framework/blob/0c0134c4b34f121c8b2201657270198425f89f28/test/resolver_sim/evidence/reachability_test.clj).

**Try it:** add an edge to an undeclared node, then add a cycle. Observe that
reachability and structural validation answer different questions.

## Example 2: content-addressed evidence nodes

PRF also has a persisted execution-evidence node format. Attestations can be
wrapped as DAG nodes and linked by parent hashes:

```clojure
(require '[resolver-sim.evidence.attestation :as att]
         '[resolver-sim.evidence.attestation-dag :as att-dag]
         '[resolver-sim.evidence.node :as node])

(def attestor {:type :ci-runner :id :ci-validation})
(def subject {:type :evidence-node :hash "sha256:example"})
(def first-attestation
  (att/build-attestation attestor subject :verified
                         {:signed-at "2026-01-01T00:00:00Z"}))
(def first-node (att-dag/build-attestation-dag-node first-attestation))

(def second-attestation
  (att/build-attestation attestor subject :reproduced
                         {:signed-at "2026-01-02T00:00:00Z"}))
(def second-node
  (att-dag/build-attestation-dag-node
   second-attestation {:parent-hashes [(:node-hash first-node)]}))

(node/validate-node-dag [first-node second-node])
(node/build-dag-index [first-node second-node])
```

`build-*` constructs and validates in memory. `emit-attestation-dag-node!`
persists a node into the configured local artifact store and registers it with
the evidence chain. That local persistence is useful for reproducible runs; it
is not network replication. See the [attestation DAG implementation](https://github.com/protocol-robustness/protocol-robustness-framework/blob/0c0134c4b34f121c8b2201657270198425f89f28/src/resolver_sim/evidence/attestation_dag.clj)
and its [tests](https://github.com/protocol-robustness/protocol-robustness-framework/blob/0c0134c4b34f121c8b2201657270198425f89f28/test/resolver_sim/evidence/attestation_dag_test.clj).

**Try it:** change the second node's parent to a missing root and inspect DAG
validation. Then compare the full node hash with the shorter display identifier
in the generated index.

## Example 3: authenticated adoption binds a history

The notebook uses `subject-decisions.v2` to sign a reviewer acceptance and a
separate ratifier adoption of the same subject. Reconstruction recomputes the
subject and verification roots under a pinned policy. A ratification without a
prior acceptance is rejected.

The current API is a deterministic local history reducer. It verifies the
history it is given; it does not establish that the supplied history is the
network's latest history. In particular, a locally valid history does not
resolve simultaneous updates or prove that another participant has the same
head.

**Try it:** in the notebook, replace the ratification's acceptance root or
subject root and re-run reconstruction. The altered decision must fail closed.

## Example 4: signed network actions are not a replicated network

PRF's researcher-challenge model derives researcher references from a network
root and public key. Actions commit to the network root, current history root,
actor, action kind, nonce, and payload. The model can replay a supplied ordered
action history and derive the resulting state.

That gives useful network-scoped identities and replay semantics, but the
functions are not a transport or shared persistence service. A network-native
DAG still needs participants to publish and fetch the same immutable nodes and
agree on how the current history advances. See the [researcher challenge
model](https://github.com/protocol-robustness/protocol-robustness-framework/blob/0c0134c4b34f121c8b2201657270198425f89f28/src/resolver_sim/network/researcher_challenges.clj)
and [researcher interaction topology notebook](../../notebooks/researcher_interaction_topology.clj).

**Try it:** submit two actions against the same history root. Both may be
individually well-formed, but a network service needs an explicit rule for
which append wins, whether the other becomes a branch, and how peers discover
that result.

## Design exercise: specify a network-published graph

Before implementing a new graph service, sketch one immutable graph envelope
that binds at least:

```clojure
{:schema "research-network-evidence-graph.v1"
 :network/root "sha256:..."
 :configuration/root "sha256:..."
 :subject/root "sha256:..."
 :basis/root "sha256:..."
 :nodes [{:artifact/root "sha256:..." :artifact/schema "..."}]
 :edges [{:edge/from "sha256:..."
          :edge/to "sha256:..."
          :edge/type :supports}]
 :history/predecessor-root "sha256:..."
 :authority-policy/root "sha256:..."}
```

This is a teaching sketch, **not an implemented PRF schema**. A production
design must define canonical projection and hashing, allowed node and edge
types, reference resolution, membership and authorization, append/fork rules,
and verification at consumption. It must also specify discovery and replication
without treating a successful HTTP response or a server-provided head as proof
of global latestness.

Use these adversarial cases as acceptance tests for a future implementation:

1. A graph references a node that cannot be fetched or whose content hashes to
   another root.
2. A signature is valid but names a different network, basis, or policy.
3. Two actions extend the same predecessor with conflicting updates.
4. A participant presents a valid old checkpoint after a newer head exists.
5. A new basis reuses approval from a superseded basis.
6. One replica omits a dissent or challenge that another replica has recorded.

The required result is explicit rejection or a policy-authorized successor
transition, never silent consensus inferred from connectivity.

## What is implemented and what is still a design target

| Capability | Current status |
|---|---|
| Structural DAG checks and reachability queries | Implemented in PRF |
| Content-addressed execution evidence nodes and local indexes | Implemented in PRF |
| Signed acceptance and ratification replay | Implemented in PRF |
| Network-scoped signed challenge actions and deterministic replay | Implemented as a model |
| Shared network storage, peer discovery, and replication of this graph | Not provided by these examples |
| Authoritative concurrent append, fork resolution, and global freshness | Requires an explicit network protocol |
| A canonical network research-graph schema with typed relationships | Design target; do not treat the sketch above as a contract |

These examples teach the current boundary. They do not qualify a network
deployment, establish production governance, or make a ratified finding an
execution authorization.
