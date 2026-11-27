(ns notebooks.util.yield-demo-sq1
  "Illustrative yield module shortfall analysis adapter.

   This is NOT the real framework runner. It is a simple sequential
   event processor built for notebook demonstration purposes.

   It uses resolver-sim.yield.partial-fill/calculate-fulfillment for
   per-user settlement decisions, but the pool allocation logic
   (how available liquidity is divided among concurrent withdrawal
   requests) is a custom proportional model.

   Label: illustrative model using real partial-fill engine."
  (:require [resolver-sim.yield.partial-fill :as pf]
            [resolver-sim.yield.position :as pos]
            [clojure.string :as str]))

(def default-module-state
  {:total-principal 0
   :available-liquidity 0
   :reserved 0
   :shortfall? false
   :available-ratio 1.0
   :fulfilled-total 0
   :deferred-total 0
   :unrequested-principal 0
   :rights {}
   :positions {}
   :outcomes []
   :events []
   :snapshots []
   :phase-label "initial"})

(defn make-position
  "Create a yield position for a user with a given principal.
   Uses the real position schema so calculate-fulfillment works correctly."
  [user-id amount]
  (pos/make-position {:owner/id (name user-id)
                      :module/id :gen-yield
                      :token :USDC
                      :principal (long amount)}))

(defn snapshot
  "Capture a module-state snapshot at an event boundary."
  [state]
  {:phase (:phase-label state)
   :total-principal (:total-principal state)
   :available-liquidity (:available-liquidity state)
   :reserved (:reserved state)
   :shortfall? (:shortfall? state)
   :available-ratio (:available-ratio state)
   :fulfilled-total (:fulfilled-total state)
   :deferred-total (:deferred-total state)
   :unrequested-principal (:unrequested-principal state)})

(defn total-remaining-principal
  "Sum of principal across all positions still active (not yet processed)."
  [state]
  (reduce + 0
    (for [[_user pos] (:positions state)
          :when (= :active (:status pos))]
      (:principal pos))))

(defn total-outstanding
  "Sum of principal remaining in ALL positions (active + unwinding)."
  [state]
  (reduce + 0 (map :principal (vals (:positions state)))))

(defn outstanding-rights
  "Sum each unique unpaid request right once, excluding unrequested principal."
  [state]
  (reduce + 0 (map :outstanding (vals (:rights state)))))

(defn unrequested-principal
  "Principal remaining in positions that is not represented by an open right."
  [state]
  (- (total-outstanding state) (outstanding-rights state)))

(defn- refresh-accounting [state]
  (assoc state
         :deferred-total (outstanding-rights state)
         :unrequested-principal (unrequested-principal state)))

(defn accounting-summary
  "Integer principal accounting for the illustrative sequential model.
   Request attempts are flows; outstanding rights are unique balances."
  [result]
  (let [state (:final-state result)
        events (:events result)
        event-requested (fn [kind]
                          (reduce + 0 (map :requested
                                           (filter #(= kind (:request-kind %)) events))))
        paid (:fulfilled-total state)
        outstanding (total-outstanding state)
        rights (outstanding-rights state)
        unrequested (unrequested-principal state)]
    {:deposited (:total-principal state)
     :cumulative-paid paid
     :outstanding-principal outstanding
     :unpaid-requested-rights rights
     :unrequested-principal unrequested
     :opening-requested (event-requested :opening)
     :newly-requested (event-requested :new)
     :retry-attempts (event-requested :retry)
     :attempted-request-total (reduce + 0 (map :requested
                                              (filter #(= :withdraw-request (:event %)) events)))
     :synthetic-failed-payment-attempts
     (count (filter #(= :simulate-payment-failure (:event %)) events))
     :principal-conserved? (= (:total-principal state) (+ paid outstanding))
     :outstanding-partitioned? (= outstanding (+ rights unrequested))
     :rights (into (sorted-map-by #(compare (str %1) (str %2)))
                   (map (fn [[id right]] [id (select-keys right [:user :outstanding :kind])])
                        (:rights state)))}))

(defn apply-event
  "Process one event against the module state.
   Returns updated state map.
   Events: :deposit, :accrue, :set-liquidity-shortfall, :withdraw-request"
  [state event]
  (let [event-type (:event event)]
    (case event-type
      :deposit
      (let [user (:user event)
            amount (long (:amount event))]
        (refresh-accounting
         (-> state
             (assoc :phase-label (str "after " (name user) " deposit"))
             (assoc-in [:positions user] (make-position user amount))
             (update :total-principal + amount)
             (update :available-liquidity + amount)
             (update :events conj event))))

      :accrue
      (let [positions (:positions state)]
        (-> state
            (assoc :phase-label "after yield accrual")
            (update :events conj event)))

      :set-liquidity-shortfall
      (let [ratio (double (:available-ratio event))
            total (:total-principal state)
            gross-available (long (* total ratio))
            already-fulfilled (:fulfilled-total state 0)
            available (max 0 (- gross-available already-fulfilled))
            reserved (- total gross-available)
            ;; Re-activate unwinding positions so their remaining principal
            ;; is counted in the new withdrawal round
            positions (reduce-kv (fn [acc user pos]
                                   (if (= :unwinding (:status pos))
                                     (assoc acc user (assoc pos :status :active))
                                     (assoc acc user pos)))
                                 {}
                                 (:positions state))]
        (let [updated (-> state
                          (assoc :phase-label (str "after shortfall (" (int (* ratio 100)) "% liquid)"))
                          (assoc :shortfall? true
                                 :available-ratio ratio
                                 :available-liquidity available
                                 :reserved reserved
                                 :positions positions)
                          (update :events conj event))]
          (refresh-accounting updated)))

      :simulate-payment-failure
      (let [user (:user event)
            right-id (:right/id event)
            amount (:amount event)
            right (get-in state [:rights right-id])]
        (when-not (and right (= user (:user right))
                       (integer? amount) (pos? amount)
                       (<= amount (:outstanding right)))
          (throw (ex-info "synthetic payment failure must identify an existing right and valid amount"
                          {:event event :outstanding-right right})))
        ;; This is a deterministic teaching event, not a token-transfer call.
        ;; It records an injected failure and deliberately changes no balances.
        (-> state
            (assoc :phase-label "after synthetic payment failure")
            (update :events conj event)))

      :withdraw-request
      (let [user (:user event)
            requested (:requested event)
            request-kind (or (:request-kind event) :opening)
            right-id (:right/id event)
            pos (get-in state [:positions user])
            prior-right (get-in state [:rights right-id])
            user-rights (reduce + 0 (for [[_ right] (:rights state)
                                         :when (= user (:user right))]
                                     (:outstanding right)))
            user-principal (long (:principal pos 0))
            user-unrequested (- user-principal user-rights)
            request-valid? (and (integer? requested) (pos? requested)
                                (contains? #{:opening :new :retry} request-kind)
                                (string? right-id) (seq right-id)
                                (case request-kind
                                  :retry (and prior-right
                                              (= user (:user prior-right))
                                              (<= requested (:outstanding prior-right)))
                                  (:opening :new) (and (nil? prior-right)
                                                       (<= requested user-unrequested))
                                  false))]
        (when-not request-valid?
          (throw (ex-info "withdrawal request is invalid for the user's remaining principal or right"
                          {:event event :outstanding-right prior-right
                           :user-unrequested-principal user-unrequested})))
        (let [state (if (= :retry request-kind)
                      state
                      (assoc-in state [:rights right-id]
                                {:right/id right-id :user user :kind request-kind
                                 :opened requested :outstanding requested}))
              pool (:available-liquidity state)
              remaining-total (total-remaining-principal state)
              ;; Proportional pool shares use exact integer division. This is a
              ;; bounded teaching model, not the canonical pro-rata allocator.
              user-pool-share (if (pos? remaining-total)
                                (quot (* pool user-principal) remaining-total)
                                0)
              effective-liquidity (min pool user-pool-share requested)
              capped-pos (assoc pos :principal (min user-principal requested))
              settlement (pf/calculate-fulfillment effective-liquidity capped-pos)
              filled (long (reduce + 0 (vals (:filled settlement {}))))
              deferred (- requested filled)
              wave (or (:wave event)
                       (inc (count (filter #(= user (:user %)) (:outcomes state)))))
              old-right (get-in state [:rights right-id])
              right-balance (- (:outstanding old-right) filled)
              outcome {:user user
                       :wave wave
                       :request-kind request-kind
                       :right/id right-id
                       :requested requested
                       :filled filled
                       :deferred deferred
                       :fill-pct (if (pos? requested)
                                   (double (/ filled requested))
                                   0.0)
                       :status (cond
                                 (zero? filled) :deferred
                                 (< filled requested) :partial-fill
                                 :else :full-fill)
                       :shortfall-affected? (pos? deferred)}
              settlement-map {:requested (:requested settlement)
                              :filled (:filled settlement)
                              :deferred (:deferred settlement)
                              :mode (:settlement-mode settlement)}
              updated (-> state
                          (assoc :phase-label (str "after " (name user) " withdrawal"))
                          (update :available-liquidity - filled)
                          (update :fulfilled-total + filled)
                          (update :outcomes conj outcome)
                          (assoc-in [:rights right-id :outstanding] right-balance)
                          (assoc-in [:settlements right-id] settlement-map)
                          (update-in [:positions user :principal] - filled))
              remaining-position (get-in updated [:positions user :principal])
              remaining-user-rights (reduce + 0 (for [[_ right] (:rights updated)
                                                      :when (= user (:user right))]
                                                  (:outstanding right)))
              updated (assoc-in updated [:positions user :status]
                                (cond (zero? remaining-position) :withdrawn
                                      (pos? remaining-user-rights) :unwinding
                                      :else :active))]
          (refresh-accounting (update updated :events conj event))))

      state)))

(defn run-scenario
  "Run a sequence of events through the module state machine.
   Returns a map with :snapshots, :outcomes, :final-state, and :events."
  [events]
  (let [initial (assoc default-module-state :phase-label "initial")]
    (loop [state initial
           remaining events
           snaps [(snapshot initial)]]
      (if (empty? remaining)
        {:snapshots snaps
         :outcomes (:outcomes state)
         :settlements (:settlements state)
         :final-state state
         :events (:events state)}
        (let [event (first remaining)
              state' (apply-event state event)
              snaps' (conj snaps (snapshot state'))]
          (recur state' (rest remaining) snaps'))))))

(defn module-snapshots
  "Extract module-level table rows from snapshots vector.
   Returns a Clerk-compatible {:head [...] :rows [[...] ...]} map."
  [snapshots]
  {:head [:t :phase :total-principal :available-liquidity :reserved :shortfall :fulfilled :unpaid-rights :unrequested]
   :rows (mapv (fn [s]
                 [(str (->> snapshots (take-while #(not= s %)) count))
                  (:phase s)
                  (str (:total-principal s) " USDC")
                  (str (:available-liquidity s) " USDC")
                  (str (:reserved s) " USDC")
                  (if (:shortfall? s) "YES" "no")
                  (if (:fulfilled-total s) (str (:fulfilled-total s) " USDC") "—")
                  (str (:deferred-total s) " USDC")
                  (str (:unrequested-principal s) " USDC")])
               snapshots)
   :row-keys [:t :phase :total-principal :available-liquidity :reserved :shortfall :fulfilled :unpaid-rights :unrequested]})

(defn withdrawal-outcomes
  "Extract per-user withdrawal outcome rows from the outcomes vector.
   Returns a Clerk-compatible {:head [...] :rows [[...] ...]} map.
   When a :user filter is given, only returns outcomes for those users."
  ([outcomes] (withdrawal-outcomes outcomes nil))
  ([outcomes user-filter]
   (let [xs (if user-filter
              (filter #(contains? (set user-filter) (:user %)) outcomes)
              outcomes)
         sorted (sort-by (juxt :user :wave) xs)]
     {:head [:user :right-id :request-kind :requested :fulfilled :fill-pct :deferred :status :shortfall-affected]
      :rows (mapv (fn [m]
                    [(name (:user m))
                     (:right/id m)
                     (name (:request-kind m))
                     (str (:requested m) " USDC")
                     (str (:filled m) " USDC")
                     (str (format "%.0f" (* 100 (:fill-pct m))) "%")
                     (str (:deferred m) " USDC")
                     (name (:status m))
                     (if (:shortfall-affected? m) "YES" "no")])
                  sorted)
      :row-keys [:user :right-id :request-kind :requested :fulfilled :fill-pct :deferred :status :shortfall-affected]})))

(defn shortfall-summary
  "Build a final shortfall summary map from the outcomes vector."
  [result]
  (let [{:keys [opening-requested newly-requested retry-attempts cumulative-paid
                outstanding-principal unpaid-requested-rights unrequested-principal
                principal-conserved? outstanding-partitioned?] :as a}
        (accounting-summary result)]
    {"Opening requests" (str opening-requested " USDC")
     "New requests" (str newly-requested " USDC")
     "Retry attempts (flows, not new rights)" (str retry-attempts " USDC")
     "Cumulative paid" (str cumulative-paid " USDC")
     "Outstanding principal" (str outstanding-principal " USDC")
     "Unique unpaid requested rights" (str unpaid-requested-rights " USDC")
     "Unrequested principal" (str unrequested-principal " USDC")
     "Principal conserved?" (str principal-conserved?)
     "Outstanding partitioned?" (str outstanding-partitioned?)
     "Attempted request total (includes retries)" (str (:attempted-request-total a) " USDC")}))

(defn event-timeline
  "Build event timeline table rows from event log.
   Returns a Clerk-compatible {:head [...] :rows [[...] ...]} map."
  [events]
  (let [rows (mapv (fn [e i]
                     (let [label (case (:event e)
                                   :deposit "Deposit"
                                   :accrue "Accrue yield"
                                   :set-liquidity-shortfall "Set shortfall"
                                   :withdraw-request "Withdraw request"
                                   (name (:event e)))
                           actor (if (:user e) (name (:user e)) "module")
                           detail (case (:event e)
                                    :deposit (str "+" (:amount e) " USDC")
                                    :accrue "5% APY for 1 year"
                                    :set-liquidity-shortfall (str "available-ratio = " (:available-ratio e))
                                    :simulate-payment-failure (str "synthetic failure on " (:right/id e)
                                                                   " for " (:amount e) " USDC; no transfer executed")
                                    :withdraw-request (str (name (or (:request-kind e) :opening)) " "
                                                           (:right/id e) ": requested " (:requested e) " USDC")
                                    (str e))]
                       [(str i) label actor detail]))
                   events (range))]
    {:head [:t :event :actor :detail]
     :rows rows
     :row-keys [:t :event :actor :detail]}))
