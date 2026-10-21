# Notebook ownership and runtime boundary

| Artifact | Owner | Notes |
|---|---|---|
| Clerk notebook sources, notebook server entry point, registry and authoring guidance | `prf-notebooks` | This repository owns their source and history. |
| PRF notebook presentation/data-view helpers under `resolver-sim.notebook-support.*` | PRF integration workspace | PRF-hosted notebooks load these through the pinned host checkout; they are not vendored here. |
| PRF simulator, allocation, yield and assurance implementations | PRF integration workspace and their extracted package owners | Notebooks call these namespaces; no semantic implementation is copied here. |
| Scenario definitions and scenario fixtures | PRF integration workspace pending scenario-level separation | Not moved with notebooks. |
| Conformance corpus, test artifacts and generated `results/` outputs | PRF integration workspace | Not moved or treated as notebook-owned truth. |
| EVM contracts and verifier implementations | `prf-evm` | Notebooks may inspect or call these tools through the host checkout. |
| Canonical encoding and allocation APIs | `prf-canonical` and `prf-pro-rata-allocation` | Used through PRF's pinned dependency graph. |

## Runtime dependencies

The notebook sources declare direct library dependencies in deps.edn and import PRF-owned resolver-sim.* namespaces where classified as prf-hosted. The runner verifies the opt-in PRF Git alias against the source pin in data/notebook-runtime.edn, prepares that revision in a managed cache, and sets its root explicitly for relative resource reads. It constructs a host classpath from the pinned source, protocol, resource, and contract directories without adding the host repository root, avoiding duplicate notebook sources. Neither host implementations nor datasets are copied into this repository.

The PRF integration workspace has not yet been migrated to consume notebook
sources from this repository. Its existing `bb notebook` workflow remains
separate; any integration should pin an immutable Git revision, update its
Clerk and Babashka paths, and keep scenario/result ownership in the PRF host.
