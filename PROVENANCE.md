# Source provenance

The initial snapshot comes from PRF integration commit
`0c0134c4b34f121c8b2201657270198425f89f28`
(`Preserve frozen evidence bundle recipe`). The source trees and file object
copied into this repository were:

- `notebooks/`: `4fd56af96d3193329a3a7dd04dbcb4e4e6a9b262`
- `docs/notebooks/`: `c422f2b0dabd19bae2abd9ca72df2d0610858b88`
- `src/resolver_sim/notebook_support/`: `83bddce744b4c29beda2f9a1c043c355753931d4`
- `src/notebooks/serve.clj`: source tree `4689665e8b46626aeb7d0b9b57d4eb08ac52646e`
- `data/notebooks.edn`: `90f243007ccc803a4e4cd965e7d6dd3ed80b16c9`

The notebook sources, support code, and documentation were copied without
modifying their contents. Their runtime paths continue to resolve against the
PRF integration checkout until that host migrates to this repository.
