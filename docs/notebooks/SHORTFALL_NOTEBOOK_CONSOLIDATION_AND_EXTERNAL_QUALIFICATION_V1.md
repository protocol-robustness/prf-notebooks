# Shortfall notebook consolidation and external qualification V1 — SNQ-01–03

This scoped record covers only SNQ-01–03, based on repository baseline `07ff7a18`. It does not qualify the notebooks as protocol implementations or expand the collection. The Linear document `PRF Shortfall Notebook Consolidation and External Qualification V1` is the normative source; this file records the disposition implemented for the first ordered slice.

## SNQ-01: claims and labels

The shortfall and recovery walkthroughs are synthetic sequential teaching adapters. They call the framework partial-fill calculation for per-user settlement, while pool availability and ordering are calculated by `notebooks/util/yield_demo.clj`. They do not test canonical allocation, deployed protocol behavior, external qualification, or real-funds execution. The separate static stress notebook's 42% and 76% values are illustrative labels, not executed counterfactuals or evidence. The withdrawal observatory describes possible interaction-risk reductions from pull-based delivery; it supplies no numerical safety score or guarantee. Registry statuses for the shortfall and recovery demonstrations are therefore `:experimental`.

## SNQ-02: first-wave principal accounting

The teaching scenario deposits 1,000 + 750 + 500 = 2,250 USDC. Opening requests are 1,000 + 500 + 500 = 2,000 USDC. The model pays 400 + 300 + 200 = 900 USDC, leaving 600 + 200 + 300 = 1,100 USDC on the three unique requested rights. Bob has a further 250 USDC of principal that was never requested. Thus:

- 2,250 deposited = 900 paid + 1,350 outstanding principal.
- 1,350 outstanding principal = 1,100 unpaid requested rights + 250 unrequested principal.

These figures are derived from scenario input and final state. They do not establish canonical fairness; canonical allocation comparison remains unrun.

## SNQ-03: retry and new-right accounting

The recovery scenario adds 1,125 USDC of liquidity (90% of 2,250 less the 900 already paid). Bob's 200 retry reuses his opening right. His 250 new request opens a distinct right against unrequested principal. In the current sequential teaching adapter, recovery payouts are Alice 500, Bob retry 200, Bob new request 193, and Cara retry 232, totaling 1,125. Integer residual rights are Alice 100, Bob opening 0, Bob new 57, Cara 68, totaling 225. Therefore 2,250 deposited = 2,025 cumulative paid + 225 outstanding principal, and the unique rights total equals outstanding principal.

The 1,125 recovery liquidity is not a promise of full payment for each retry/new request. The illustrative adapter allocates sequential integer pool shares; the request amounts total 1,350. Any source text that says each recovery request fully pays, or implies that retrying creates a new liability, is incorrect. Retries are attempt flows; the rights map stores each unpaid balance once.

This distinguishes available recovery capital from requested demand: the prior note that each request was fully paid would be arithmetically wrong because 1,350 was requested against 1,125 available. The exact per-attempt payout/residual split above is the current adapter's result and should be carried into any clarification of the normative scenario.

## Scoped inventory and evidence boundary

| Notebook | Disposition in this slice | Evidence boundary |
| --- | --- | --- |
| `notebooks/yield_shortfall_partial_withdrawal_fills.clj` | Experimental first-wave accounting; explicit synthetic-model limits and derived partition | Framework partial-fill helper plus custom sequential pool adapter; no canonical allocator run |
| `notebooks/partial_liquidity_recovery_deferred_claim.clj` | Experimental recovery example; Bob retry and new request have separate IDs and exact integer results | Same teaching adapter; no deployed or real-funds execution |
| `notebooks/demo_liquidity_shortfall_partial_settlement.clj` | Static stress values labeled synthetic; no replay/evidence claim | Hardcoded display data only |
| `notebooks/withdrawal_observatory.clj` | Pull/push interaction discussion with no safety score or guarantee | Conceptual comparison only |

Historical evaluation records remain historical artifacts and are not evidence that the revised source or accounting has been qualified. No protocol source or execution behavior is changed by this slice.
