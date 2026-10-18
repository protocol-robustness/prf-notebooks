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

The repository declares its direct Clojure, Clerk, JSON, schema, and hiccup
dependencies in `deps.edn`. The notebook code also imports many
`resolver-sim.*` implementation namespaces owned by PRF. For local import
checks, resolve the classpath from this repository with
`clojure -P -M:prf-host:clerk`. The `:prf-host` alias pins PRF source revision
`0c0134c4b34f121c8b2201657270198425f89f28`; it is a development bridge, not a
published runtime package dependency. Full evaluation of notebooks that open
relative paths such as `scenarios/`, `data/`, `etc/`, and `results/` requires
the PRF checkout root as the working directory. That host integration is still
pending, so the standalone extraction does not yet provide a one-command way
to combine its dependency file with the host working directory.

This gives the extracted repository a reproducible source classpath, while
notebook evaluation still depends on the pinned host's implementation namespaces
and PRF-owned datasets. It does not make those datasets portable or move their
semantics here.

The source snapshot is recorded in `PROVENANCE.md`. No notebook package or
rendered site has been published from this repository.
