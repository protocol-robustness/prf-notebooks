^{:nextjournal.clerk/toc true
  :nextjournal.clerk/visibility {:code :fold}}
(ns notebooks.portable-canonical-package
  "A small runnable example of the extracted prf-canonical library."
  (:require [nextjournal.clerk :as clerk]
            [prf.canonical.codec :as codec]
            [prf.canonical.hash :as canonical-hash]))

(def example-value
  {:account "alice"
   :amount 23
   :active? true})

(def encoded-hex
  (codec/canonical-bytes-hex example-value))

(def example-root
  (canonical-hash/hash-with-domain
   "prf-notebooks.portable-canonical-example.v1"
   example-value))

(clerk/html
 [:main
  [:h1 "Portable canonical package example"]
  [:p "This notebook calls prf-canonical directly from its pinned package revision."]
  [:h2 "Canonical value"]
  [:pre (pr-str example-value)]
  [:h2 "Canonical bytes (hex)"]
  [:pre {:data-output "canonical-bytes"} encoded-hex]
  [:h2 "Example domain root"]
  [:pre {:data-output "example-root"} example-root]])
