# PRF Notebooks

This repository owns the PRF Clerk notebook sources, notebook-only rendering
helpers, notebook registry, and notebook authoring guidance.

The notebooks are views over the PRF integration workspace. They use its
simulation namespaces and read scenario, fixture, conformance, and generated
result files from that checkout. The repository preserves those references;
it does not copy or redefine their economic semantics or data ownership.

## Repository contents

- `notebooks/`: executable Clerk notebook sources and their local configuration.
- `src/resolver_sim/notebook_support/`: notebook presentation and data-view
  helpers.
- `src/notebooks/serve.clj`: Clerk server entry point.
- `data/notebooks.edn`: notebook registry consumed by the index.
- `docs/notebooks/`: authoring, style, and export guidance.

## Runtime

Use this repository as a pinned source dependency of the PRF integration
workspace. Evaluate notebooks with the PRF project classpath and from the PRF
checkout root so relative paths such as `scenarios/`, `data/`, `etc/`, and
`results/` resolve to their owning datasets. This source extraction does not
make those datasets portable or move simulation implementations into this
repository.

The source snapshot is recorded in `PROVENANCE.md`. No notebook package or
rendered site has been published from this repository.
