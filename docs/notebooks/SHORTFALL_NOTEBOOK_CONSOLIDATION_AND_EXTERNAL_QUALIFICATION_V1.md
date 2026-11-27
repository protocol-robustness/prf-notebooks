# PRF Shortfall Notebook Consolidation and External Qualification V1

**Status:** Proposed implementation contract · 27 November 2026  
**Owners:** prf-notebooks (presentation, registry, runner and export); PRF core / allocation / EVM owners (economic semantics, source artifacts and verifiers); prf-site and infrastructure owners (publication).  
**Scope:** A deliberately small externally shareable shortfall collection, not a full notebook release or production protocol assurance.

## 1. Objective and baseline

Provide a progressive research journey from detecting scarce liquidity to comparing policies, protected settlement, recovery and independent investigation. Every reported result must be attributable to an exact workload and appropriate evidence strength. Readers must be able to distinguish simulations, retained local observations, external source authentication, and researcher acceptance.

**Review baseline:** [notebook source 07ff7a18](https://github.com/protocol-robustness/prf-notebooks/tree/07ff7a18da15cfc6402de199179fe17457b8c253), [release manifest](https://github.com/protocol-robustness/prf-notebooks/blob/07ff7a18da15cfc6402de199179fe17457b8c253/RELEASE.edn), [pinned host 0c0134c4](https://github.com/protocol-robustness/protocol-robustness-framework/tree/0c0134c4b34f121c8b2201657270198425f89f28), and [reviewed core integration 84ca777e](https://github.com/protocol-robustness/protocol-robustness-framework/tree/84ca777e0fca16030c40f158cf593b2a45184c73). These are review references, not a mandate to regress newer work. First inventory actual current revisions and reconcile findings. No qualification is inferred from existing registry labels.

## 2. Required public learning path

Five curated entries (reuse/refactor sources rather than creating five parallel implementations):

1. **Shortfall Fundamentals:** Define liquidity shortage versus impairment/insolvency; opening statement, eligible demand, available/restricted capital, outstanding rights, deficit and basis. Show initial state and explicit assumptions.
2. **Shortfall Allocation & Fairness:** Contrast sequential settlement/first-come-first-served and canonical committed pro-rata under identical frozen eligible rows and capital, plus rounding, caps and redistribution policy. Separate arrival order, allocation order, claim execution order. Expose a counterexample and controls; do not imply universal fairness or incentive compatibility.
3. **Protected Shortfall Settlement:** Retain composed pro-rata V2 as flagship: source/context → realized allocation → proof/admission → authorization/funding → beneficiary claims → accounting/closure. Identify V1 proof, V2 preparation/allocator and V3 reserve lifecycle; distinguish local Foundry observations from mined receipts. For the retained fixture show 100 requested, 70 allocated, 30 unmet, 35/21/14 claimant allocations and the separate 10-unit restricted refund. Unmet demand is not automatically a deferred right. Never count refund as claimant entitlement.
4. **Shortfall Progression & Recovery:** Show ordered opening, payments, recovery, revised rights and closing/current statements. Show failed transfer/retry and reordered claims when supported by exact evidence; illustrate open/censored episodes, partial and full recovery. Reuse `shortfall-recovery-episode.v2` and accounting read-back rather than extending notebook-only economic logic.
5. **Shortfall Forensics & Qualification:** Show source identities, candidate qualification, replay/verification, exact statement and artifact roots, negative tests, evidence graph and reviewer/challenge status. Expose a bounded policy comparison from Challenge 001 or another independently reproducible case; identify candidate versus approved challenge and avoid implying authority or ratification. Show independent implementation versus same-implementation replay.

Each entry must provide a plain-language question, one primary takeaway, explicit classification, readable opening and closing/current statement, mechanism/policy, steps, evidence and limitations, reproduction instructions, linked concepts, and deeper technical appendix. Provide text-equivalent tables for visuals. Archive/reclassify legacy material and preserve links or redirects; do not silently delete historical evidence.

## 3. Normative economic contract

Use existing canonical artifact schemas and integer units, with asset identity and decimals explicitly declared; notebook presentation must not own or reinterpret economic truth. If a required canonical statement does not exist, document the gap and coordinate upstream; never fabricate one or silently promote illustrative data. Distinguish:

- Candidate universe, eligibility/disposition, requested amount, allocation, unpaid requested amount, unrequested principal, outstanding obligations, realized transfers, recognized losses, restricted reserves, allocator residual, and available capital (authority- and operation-scoped).
- Request identity versus entitlement/beneficiary identity; original request, retry, new request, cancellation, liability reduction, haircut and payment as distinct events.
- Opening, realized, recovery opening and current/closing statements with predecessor/decision roots, asset/unit, time/order and evidence basis. Recovery opening must derive from actual reconciled predecessor effects, not stale intended allocation.
- In a fixed principal-only/no-loss model: deposited principal = cumulative paid principal + outstanding principal; outstanding principal = unpaid requested principal + unrequested principal (for the partitioned example). Requested amount = fulfilled + unfilled per valid attempt, not necessarily outstanding across attempts. Outstanding unpaid rights are reconstructed by unique identity, not by summing historical deferred events.
- Deficit = max(eligible outstanding obligation - operation-available liquidity, 0) for the stated basis. Distinguish shortfall against requests from total exposure. Payments reduce matching rights and liquidity by the same amount; absent other changes, the positive deficit remains constant. Recovery capital increases liquidity; liability reductions affect obligations only; new rights require admitted decision lineage. Reconcile each transition; no negative balance, overpayment, double claim, or unauthorized use of restricted capital. Respect cap, rounding, residual and conservation invariants of the exact policy.
- No automatic assumption that all unfilled demand is a liability or loss. No assertion that 100% liquidity is necessary for an individual deferred claim to be fully paid. Unknown onset/closure or unauthenticated source remains explicitly unknown.

## 4. Implementation requirements and regression acceptance

### SNQ-01 / P0: Inventory, containment and classification (F-04, F-07)

Inventory each relevant registry entry, source revision, artifact, status, dependencies and public URL. Immediately remove/reclassify unsupported verified claims. Static demo 42%/76% and claimed counterfactual replay must be labelled synthetic with no evidence-output claims, or replaced by actual executed results. Withdrawal observatory's 100% safe/0 risk scores must be removed; pull architecture reduces certain risks but is not a safety guarantee. Keep legacy slashing demos separately labelled, not settlement evidence.

### SNQ-02 / P0: Correct first-wave accounting (F-01, F-03)

For legacy stated inputs: deposits Alice 1000, Bob 750, Cara 500; requests 1000/500/500; available 900. Stated sequential outputs 400/300/200 imply deferred requests 600/200/300, Bob unrequested 250, outstanding 1350, and current unpaid requests 1100. Assert these separately if retaining the legacy algorithm; do not change expected results just to pass. Explicitly mark its active-only sequential denominator and custom allocation as illustrative. Compare against canonical allocation on equivalent committed inputs and disclose different policies; do not represent legacy sequence as canonical fairness. Derive display figures from validated outputs, not hardcoded prose.

### SNQ-03 / P0: Correct recovery accounting (F-02)

Distinguish Bob's 200 retry from 250 new request, or change the scenario explicitly and requalify. For the retained 40%→90% schedule on 2250 original principal, available incremental capital = 1125 after 900 paid, assuming the stated schedule; if fully paid, cumulative paid 2025 and outstanding 225. Confirm actual per-user outputs under integer rounding; do not assume full distribution. Assert unique outstanding rights, retries without liability inflation, incremental vs cumulative amounts and closure status. Include multiple recovery waves, partial request, full payment, zero recovery, failed payment/retry, duplicate claim and new entitlement cases. Replace/adapt the helper to consume canonical recovery evidence; any retained teaching adapter must be clearly bounded and tested.

### SNQ-04 / P0: Reproduction/runtime migration (F-05)

Replace obsolete `bb demo:public-lab` and `-M:with-sew` instructions with actual extracted runner and local serve commands verified at the selected commit. No mandatory JJ dependency; use immutable Git revision/file identity and optional local diagnostics without leaking paths or working-copy details. Pin notebook, PRF host, portable allocator, canonical library, exact prf-evm checkout/evidence, other relevant dependencies and fixture roots. Managed explicit opt-in for external checkouts; no accidental sibling checkout, shadowed namespaces, unpinned script or fallback. Record exact commands, resolved dependency identities, expected semantic markers, output hashes and scope. Separate stable semantic output from nondeterministic UI/runtime metadata.

### SNQ-05 / P0: Scenario and general yield projection truthfulness (F-06)

Re-run Y04/Y05 and related suite on pinned host; investigate retained expectation mismatches (nil status/metric readings and deferred/reclaimed fields). Zero invariant violations is not a pass. Fix root cause upstream or record exact failing assertions, disposition and restricted claim. Verify `yield_demo` view model against authoritative replay/position/transfer fields: principal, expected yield, fulfilled, deferred, haircut, loss and recovery; reject conflating balance or accrued yield with actual payout; do not default missing fields to verified zero/fully liquid. Workbench must preserve failure/timeout state and expose requested/performed coverage. Never overwrite failing fixtures to turn them green without justified semantic review.

### SNQ-06 / P1: Canonical statements, mechanisms and learning path

Implement §2, consuming §3 and existing upstream read-back interfaces. Include integer/rounding/cap/tie-break cases, omitted/ineligible claimant, stale or substituted opening root, allocation-vs-execution order, recovery from actual effects and closure with outstanding obligations. Source-authentication and eligibility limitations must be visible. If canonical inputs are absent, mark unavailable or illustrative, not qualified.

### SNQ-07 / P1: Shared qualification projection and fail-closed export

Implement §5 using actual artifacts; no self-reported promotion. Render failure and unknown states; no successful release on missing required check, timeout, stale pin, tampered artifact, contradictory amount, unsupported claim or missing reviewer evidence. Draft/illustrative material can be intentionally shared with scoped labels; it must not appear in a qualified collection as qualified.

### SNQ-08 / P1: Independent review, clean checkout and publication handoff

Verify all selected entries with §6, then update registry and release manifest for exact scope. Coordinate with site/infra to stage and verify published immutable revisions, navigation/downloads and accessibility. No live deployment, approval, or public-chain claims are inferred from a build. Preserve archived routes and previous qualified exports.

## 5. Qualification profile

Maintain a machine-readable profile per notebook **and exact rendered candidate**, with claim ID/scope, source/fixture/dependency identities, applicable checks, requested/achieved strength, performed checks, verifier revision, retained evidence references, limitations and review disposition.

Track independent dimensions: illustration/model classification; economic reconciliation; deterministic replay; independent implementation comparison; structural/integrity/binding/semantic verification; source authentication; contract execution (none/model/local EVM/testnet/mainnet with explicit evidence); researcher review/ratification; operational/production qualification; and publication status. Each uses explicit applicable status (`pass`/`fail`/`not-run`/`not-applicable`/`blocked` or `verified`/`invalid`/`unknown` as appropriate); missing evidence cannot equal pass. Require a reason for N/A and a precise bound for every claim.

A hash proves identity/integrity only under its assumptions, not truth or authority. Local validation of retained evidence is not a fresh execution or external source authentication. Where independent researcher acceptance is claimed, use existing policy/roster, authenticated decisions, complete challenge/disposition/closure and required threshold; do not fabricate consensus or a new threshold in notebook code. An independent engineering review for publication is not researcher ratification. Materially different claims require their own evidence and disposition.

## 6. Release acceptance (all applicable gates)

- Inventory and dispositions are complete; F-01–F-07 are closed or explicitly scoped/excluded with reviewer-accepted rationale, including the actual yield projection audit.
- Five curated entries are readable without prior Sew knowledge, with clear labels and opening/realized/recovery/current statements. Economic assertions and visual values are checked against canonical outputs; scenario-specific and cross-surface regressions pass. Negative tests reject stale, tampered, missing, reordered or substituted evidence where relevant.
- A fresh isolated checkout from an unrelated working directory uses the documented runner; no existing PRF/EVM checkout or JJ is assumed; required runtime/setup is explicit. Execute each selected notebook and retain exit codes, logs, meaningful expected markers, source/fixture/dependency hashes, stable semantic output and rendered artifact hashes. Test missing dependencies and timeouts; fail closed.
- An independent reviewer who did not author the change checks the exact pinned candidate, accounting, claim scope, evidence, reproduction and limitations. Record disposition, reviewer identity and reviewed root/revision; re-review material changes. Distinct implementation reproduction is claimed only where actually performed.
- The staged site render matches qualified output; verify links, evidence downloads, desktop/mobile and plain-text readability, no stale source pin or status mismatch; record a deployment/publication receipt only after remote verification. A subset qualification is explicitly named and does not set full-registry qualification to pass.
- Deliver source changes, tests, migration/redirect mapping, qualification records, exact rendered bundle, independent review and publication handoff/receipt as applicable. No production assurance, security audit, protocol change, paid challenge activation, or real-funds execution is implied.

## 7. Coordination and non-goals

Reuse [Shortfall Pro-Rata V1](https://github.com/protocol-robustness/protocol-robustness-framework/blob/84ca777e0fca16030c40f158cf593b2a45184c73/docs/allocation-proofs/SHORTFALL_PRO_RATA_V1.md), [Shortfall Recovery Evidence V2](https://github.com/protocol-robustness/protocol-robustness-framework/blob/84ca777e0fca16030c40f158cf593b2a45184c73/docs/yield/SHORTFALL_RECOVERY_EVIDENCE.md), [Challenge 001](https://github.com/protocol-robustness/protocol-robustness-framework/blob/84ca777e0fca16030c40f158cf593b2a45184c73/docs/research/CHALLENGE_001_SHORTFALL_SCOPE_CANDIDATE.md) and [first-result qualification handoff](https://github.com/protocol-robustness/protocol-robustness-framework/blob/84ca777e0fca16030c40f158cf593b2a45184c73/docs/research/SHORTFALL_FIRST_RESULT_QUALIFICATION_HANDOFF.md).

Coordinate with [PRO-11](https://linear.app/protocol-robustness-framework/issue/PRO-11/specify-and-independently-test-partial-fill-entitlement-semantics), [PRO-41](https://linear.app/protocol-robustness-framework/issue/PRO-41/independently-rederive-partial-fill-evidence-semantics-from-source), [PRO-20](https://linear.app/protocol-robustness-framework/issue/PRO-20/verify-published-demo-manifests-independent-reproduction-and-version), [PRO-42](https://linear.app/protocol-robustness-framework/issue/PRO-42/add-forensic-notebook-exports-and-links-for-partial-fill-and-pro-rata), [PRO-37](https://linear.app/protocol-robustness-framework/issue/PRO-37/complete-or-truthfully-fail-notebook-validation-across-all-51), [PRO-172](https://linear.app/protocol-robustness-framework/issue/PRO-172/prf-accounting-read-back-v1-opening-realized-and-recovery-positions), [PRO-187](https://linear.app/protocol-robustness-framework/issue/PRO-187/vh-15-qualify-recovery-starting-statements-from-predecessor-and-actual), [PRO-188](https://linear.app/protocol-robustness-framework/issue/PRO-188/rb-p1-freeze-common-accounting-read-back-presentation-and-verification), [PRO-191](https://linear.app/protocol-robustness-framework/issue/PRO-191/rb-p4-recovery-opening-read-back-adapter-using-verified-state-and), [PRO-199](https://linear.app/protocol-robustness-framework/issue/PRO-199/vh-16-qualify-the-starting-pro-rata-allocation-basis-before), [PRO-200](https://linear.app/protocol-robustness-framework/issue/PRO-200/rb-p2a-render-a-source-qualified-starting-pro-rata-statement), and [PRO-213](https://linear.app/protocol-robustness-framework/issue/PRO-213/reo-10-establish-pro-rata-application-ownership-and-qualification).

Reuse existing contracts; do not duplicate allocators, canonical hashing, evidence verifier, accounting read-back or release gates. Related tasks are not automatically hard blockers; missing required evidence blocks corresponding claims only. Use normal independent integration qualification and guarded promotion; no direct main promotion from this task. Suggested repository mirror: `prf-notebooks/docs/notebooks/SHORTFALL_NOTEBOOK_CONSOLIDATION_AND_EXTERNAL_QUALIFICATION_V1.md` through reviewed change. Keep one curated path, not a parallel research platform.

## 8. Agent handoff

Begin SNQ-01, then SNQ-02/03; run SNQ-04/05 in parallel where ownership permits. Only then promote canonical narrative and shared qualification. Report baseline/target revisions, changed paths, before/after equations, actual tests/results, outstanding defects, claim exclusions and independent review evidence. Do not declare Done based on render alone.

## Implementation note for the SNQ-01–03 slice

The recovery teaching adapter now supports one injected `:simulate-payment-failure` event. It checks that the event identifies an existing request right and an amount within that right, records a synthetic failure marker, and changes no paid amount, liquidity, position principal, or right balance. A subsequent retry can then settle that same right. This is a bookkeeping regression case only: the adapter does not execute, observe, or model an external token transfer, and the notebook makes no real-transfer failure claim. Transfer-level failure and recovery remain unqualified pending canonical recovery evidence.

For the retained 40%→90% scenario, the current adapter outputs Alice 500, Bob's retry 200, Bob's new request 193, and Cara 232 paid from 1,125 recovered liquidity. The unique unpaid balances are Alice 100, Bob's opening right 0, Bob's new right 57, and Cara 68; cumulative paid is 2,025 and outstanding principal is 225. This satisfies `2,250 = 2,025 + 225`; the 1,125 capital is less than the 1,350 recovery request flow, so it does not imply every attempt is fully paid.
