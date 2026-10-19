# PRF Notebooks

This repository owns the PRF Clerk notebook sources, notebook-only rendering
helpers, notebook registry, and notebook authoring guidance.

The notebooks are views over the PRF integration workspace. They use its
simulation namespaces and read scenario, fixture, conformance, and generated
result files from that checkout. The repository preserves those references;
it does not copy or redefine their economic semantics or data ownership.

Notebook results are exploratory evidence for each notebook's declared,
pinned workload. A successful render or evaluation does not by itself establish
that PRF or an application is complete for general use, production-ready, or
independently qualified; consult the notebook's recorded validation and scope.

## Repository contents

- `notebooks/`: executable Clerk notebook sources and their local configuration.
- `src/notebooks/serve.clj`: Clerk server entry point.
- `data/notebooks.edn`: notebook registry consumed by the index.
- `docs/notebooks/`: authoring, style, and export guidance.

## Runtime

The runner requires Babashka and the Clojure CLI.

bin/prf-notebooks is the setup and evaluation entry point. It works when invoked from another working directory.

    bin/prf-notebooks list
    bin/prf-notebooks setup --standalone
    bin/prf-notebooks run hardening-artifact

    bin/prf-notebooks run portable-canonical-package --with-packages
    bin/prf-notebooks run report --with-prf-host

`bin/prf-notebooks` does not start a Clerk server. The `src/notebooks/serve.clj`
entry point is invoked by `bb notebook` from the PRF integration checkout, where it
uses the pinned host classpath and fixture resources.

The registry classifies every entry as standalone, package-dependent, or prf-hosted. Standalone dependency resolution is an explicit setup --standalone step. Package and PRF host options are explicit opt-ins; if a managed checkout is missing, that selected command prepares it at the locked revision before evaluation. setup --packages, setup --prf-host, and setup --all prepare sources and resolve dependencies ahead of evaluation.

`bin/prf-notebooks list` validates that every runnable registry entry has a
source file in this repository. Archived metadata lives under
`:archived-notebooks` and is excluded from runnable navigation and setup.
Runner checks are available with `PRF_NOTEBOOKS_REPO="$PWD" clojure -Srepro
-M:test`.

Sources are cached under ~/.cache/prf-notebooks, or under PRF_NOTEBOOKS_CACHE. The runner only creates and checks out directories inside this managed cache. It does not change an existing PRF checkout. Its PRF Git alias and checkout URL/revision are cross-checked against data/notebook-runtime.edn before setup or evaluation.

The runner passes the managed PRF root as the JVM user.dir for hosted notebooks. Relative reads of PRF-owned data/, etc/, results/, and scenarios/ therefore resolve against that explicit root regardless of the caller's directory. Qualification records include the actual source revision, fixture tree identities, normalized resolved classpath, output marker checks, and output hashes.

portable-canonical-package imports the pinned prf-canonical package directly. The runner checks for duplicate prf.canonical source namespaces on the resolved classpath. The example uses its own domain tag and does not change any PRF canonical identities.

For PRF-hosted notebooks, the runner builds a restricted classpath from the
pinned PRF checkout's `src`, protocol, resource, and contract paths. It excludes
the host repository root, so its notebook files do not shadow this repository's
sources. Host presentation helpers load from the pinned PRF source tree; they
are not copied here. The projection omits PRF's older `src/notebooks/serve.clj`
because this repository owns the server entry point. Canonical namespaces may
never appear twice.

The source snapshot is recorded in `PROVENANCE.md`. No notebook package or
rendered site has been published from this repository.
