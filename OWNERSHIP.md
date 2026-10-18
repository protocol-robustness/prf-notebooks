# Notebook ownership and runtime boundary

| Artifact | Owner | Notes |
|---|---|---|
| Clerk notebook sources, shared presentation helpers, notebook server entry point, registry and authoring guidance | `prf-notebooks` | This repository owns their source and history. |
| PRF simulator, allocation, yield and assurance implementations | PRF integration workspace and their extracted package owners | Notebooks call these namespaces; no semantic implementation is copied here. |
| Scenario definitions and scenario fixtures | PRF integration workspace pending scenario-level separation | Not moved with notebooks. |
| Conformance corpus, test artifacts and generated `results/` outputs | PRF integration workspace | Not moved or treated as notebook-owned truth. |
| EVM contracts and verifier implementations | `prf-evm` | Notebooks may inspect or call these tools through the host checkout. |
| Canonical encoding and allocation APIs | `prf-canonical` and `prf-pro-rata-allocation` | Used through PRF's pinned dependency graph. |

## Runtime dependencies

The notebook sources expect a PRF integration checkout as their host. They
refer to `resolver-sim.*` namespaces and open paths beneath `data/`, `etc/`,
`results/`, and `scenarios/`. `deps.edn` exposes notebook and helper source paths
for a host project; it does not declare or copy the PRF simulation or data
repositories.

The PRF integration workspace has not yet been changed to consume this
repository. Migration must pin an immutable Git revision, update Clerk and
Babashka paths that currently assume local `notebooks/`, and keep all
scenario/result ownership in the PRF host.
