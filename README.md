# PRF Notebooks

This repository owns the PRF Clerk notebook sources, notebook-only rendering
helpers, notebook registry, authoring guidance, and local Clerk server.

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

Start with the [research-network DAG learning path](docs/notebooks/research-network-dag-learning-path.md)
for examples that distinguish graph reachability, authenticated ratification,
and the additional pieces needed for network-published evidence.

## Runtime

The local Clerk server requires Java 17 or newer and the Clojure CLI. The first
run fetches the pinned PRF dependency and other Maven artifacts, so it needs
network access. Babashka is additionally required for the `bin/prf-notebooks`
runner.

bin/prf-notebooks is the setup and evaluation entry point. It works when invoked from another working directory.

    bin/prf-notebooks list
    bin/prf-notebooks setup --standalone
    bin/prf-notebooks run hardening-artifact

    bin/prf-notebooks run portable-canonical-package --with-packages
    bin/prf-notebooks run report --with-prf-host

Start the repository-owned Clerk server from this checkout with the pinned PRF
implementation on its classpath:

    clojure -M:prf-host:serve

Run the command from the repository root, then open
<http://localhost:7777/notebooks/index.clj>, the
[Read-only Reachability notebook](http://localhost:7777/notebooks/read_only_reachability.clj),
or the [Reachability and Ratification notebook](http://localhost:7777/notebooks/reachability_and_ratification.clj).
The server and notebook sources come from this repository; the PRF dependency
provides implementation namespaces and fixture resources at the revision pinned
in `deps.edn`.

If port 7777 is already in use, choose another local port:

    PRF_NOTEBOOK_PORT=7779 PRF_NOTEBOOK_API_PORT=7780 clojure -M:prf-host:serve

This command starts a local Clerk process; it does not publish the notebooks.
The public PRF website serves Clerk through infrastructure-managed services.
That deployment is configured separately in the `infra` repository and must
pin this repository's immutable revision before it can serve these sources.

The registry classifies every entry as standalone, package-dependent, or prf-hosted. Standalone dependency resolution is an explicit setup --standalone step. Package and PRF host options are explicit opt-ins; if a managed checkout is missing, that selected command prepares it at the locked revision before evaluation. setup --packages, setup --prf-host, and setup --all prepare sources and resolve dependencies ahead of evaluation.

`bin/prf-notebooks list` validates that every runnable registry entry has a
source file in this repository. Archived metadata lives under
`:archived-notebooks` and is excluded from runnable navigation and setup.
Runner checks are available with `PRF_NOTEBOOKS_REPO="$PWD" clojure -Srepro
-M:test`.

Before proposing source changes, validate the registry and runner:

    bin/prf-notebooks list
    PRF_NOTEBOOKS_REPO="$PWD" clojure -Srepro -M:test

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

The source snapshot is recorded in `PROVENANCE.md`. Release qualification and
publication status are tracked separately in `RELEASE.edn`; a passing notebook
render is not a release qualification.
