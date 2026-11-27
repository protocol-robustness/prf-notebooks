^{:nextjournal.clerk/visibility {:code :fold}
  :nextjournal.clerk/dark-mode true}
(ns notebooks.withdrawal-observatory
  (:require [nextjournal.clerk :as clerk]))

;; # Withdrawal & Claimable Balance Observatory
;; ## Illustrative Settlement Architecture: Push vs. Pull

^{:nextjournal.clerk/visibility {:code :hide :result :show}
  :nextjournal.clerk/width :full}
(clerk/html
 [:div.workbench-container {:style {:background "#020617" :color "#7ADDDC" :padding "60px" :fontFamily "'JetBrains Mono', monospace"}}
  [:style "
    .observatory-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; }
    .arch-card { background: #0f172a; border: 1px solid #004D59; padding: 32px; border-radius: 4px; }
    .flow-dag { height: 300px; display: flex; align-items: center; justify-content: space-around; }
    .node { width: 100px; height: 50px; background: #020617; border: 1px solid #7ADDDC; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 800; position: relative; }
    .line { height: 2px; background: #004D59; flex: 1; position: relative; }
    .line::after { content: ''; position: absolute; right: 0; top: -4px; border-left: 8px solid #004D59; border-top: 5px solid transparent; border-bottom: 5px solid transparent; }
    .h1-title { font-size: 2.5rem; font-weight: 900; margin-bottom: 40px; color: #fff; }
    .comparison-box { margin-top: 40px; border: 1px solid #004D59; padding: 24px; }
  "]

  [:h1.h1-title "PULL-BASED SETTLEMENT OBSERVATORY"]
  
  [:div.observatory-grid
   ;; Left: Push Model (Risk)
   [:div.arch-card
    [:h3 {:style {:color "#FF9800"}} "Push Model (interaction risks)"]
    [:div.flow-dag
     [:div.node "ESCROW"]
     [:div.line {:style {:background "#FF9800"}} ]
     [:div.node "RECIPIENT"]]
    [:p {:style {:fontSize "0.9rem" :color "#cbd5e1"}} 
     "A push flow couples settlement to recipient interaction. Depending on implementation, this can expose reentrancy or gas griefing risks; this diagram does not establish an exploit or safety result."]]

   ;; Right: Pull Model (Sew)
   [:div.arch-card
    [:h3 {:style {:color "#03DAC6"}} "Pull Model (separated delivery)"]
    [:div.flow-dag
     [:div.node "ESCROW"]
     [:div.line]
     [:div.node "CLAIMABLE" [:br] "LEDGER"]
     [:div.line]
     [:div.node "WITHDRAWAL"]]
    [:p {:style {:fontSize "0.9rem" :color "#cbd5e1"}} 
     "Separating entitlement from delivery can reduce some recipient-interaction risks. A pull design still requires correct authorization, accounting, and implementation; it is not a safety guarantee."]]]

  ;; Governance Constraint Matrix
  [:div.comparison-box
   [:h3 "Design considerations"]
   [:p "This conceptual comparison has no measured safety score and does not test a deployed implementation."]
  ]])
