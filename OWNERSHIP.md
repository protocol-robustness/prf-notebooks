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

The notebook sources declare their direct library dependencies in `deps.edn`
and import PRF-owned `resolver-sim.*` namespaces. The opt-in `:prf-host` alias
pins a PRF source revision for development evaluation; it is not a package
runtime dependency. Evaluation must run from a PRF checkout root because
notebooks open paths beneath `data/`, `etc/`, `results/`, and `scenarios/`.
Neither those implementations nor datasets are copied into this repository.

The PRF integration workspace has not yet been changed to consume this
repository. Migration must pin an immutable Git revision, update Clerk and
Babashka paths that currently assume local `notebooks/`, and keep all
scenario/result ownership in the PRF host.
