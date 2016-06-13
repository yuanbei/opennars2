(ns narjure.defaults)

(def belief-frequency 1.0)
(def belief-confidence 0.9)

(def truth-value
  [belief-frequency belief-confidence])

(def belief-priority 0.6)
(def belief-durability 0.2)
;todo clarify this
(def belief-quality 0.0)

(def belief-budget
  [belief-priority belief-durability belief-quality])

(def question-priority 0.9)
(def question-durability 0.7)
;todo clarify this
(def question-quality 0.0)

(def question-budget
  [question-priority question-durability question-quality])

(def goal-confidence 0.9)
(def goal-priority 0.99)
(def goal-quality 0.0)
(def goal-durability 0.8)

(def goal-budget
  [goal-priority goal-durability goal-quality])

(def budgets
  {:belief belief-budget
   :question question-budget
   :goal goal-budget
   :quest question-budget})

(def c-priority 0.5)                                        ; default concept priority

(def ^{:type double} horizon 1)                             ; personality factor for evidential horizon
(def max-evidence 50)                                       ; maximum length of evidence trail
(def temporal-window-duration 10)                           ; number of system cycles to consider as concurrent
(def max-term-complexity 15)                                ; maximum number of terms and sub terms in a statement - syntactic complexity
(def priority-threshold 0.001)                              ; concepts and tasks have to be above this to be processed
(def selection-count 10)                                    ; don't set higher if not on a sumpercomputer, will cause trouble
(def termlink-single-sample-evidence-amount 0.01)           ; default conf for termlink truth value
(def concept-max-termlinks 5)                               ; max size of termlink bag per concept
(def max-tasks 10)                                          ; max size of task bag per concept
(def max-anticipations 5)                                   ; max size of anticipation bag per concept
(def max-concepts 1000)                                     ; do not make too small (less than 50) as causes cyclic issue between task-dispatcher and concept-manager
(def max-derived-sentences 50)                              ; task bag capacity
(def max-derived-selections 10)                             ; max derived selections per cycle
(def max-event-selections 10)                               ; no longer used - number of events to select per cycle
(def max-events 50)                                         ; no longer used
(def decay-rate 100)                                        ; forgetting adjustment rate for concepts e^-lt where l = (1.0 - durabiity) / decay-rate
                                                            ; durability of 0.5 and decay rate of 100 fully decays priority in 1000 cycles
                                                            ; decay-rate of 10 would fully decay it in 100 cycles