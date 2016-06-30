(ns narjure.memory-management.local-inference.belief-processor
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.bag :as b]
    [narjure.debug-util :refer :all]
    [narjure.control-utils :refer :all]
    [narjure.global-atoms :refer :all]
    [narjure.defaults :refer :all]
    [narjure.perception-action.task-creator :refer [event? get-id]]
    [narjure.memory-management.local-inference.local-inference-utils :refer :all]
    [nal.term_utils :refer :all]
    [nal.deriver.truth :refer [t-or confidence frequency w2c t2-evidence-weights]]
    [nal.deriver.projection-eternalization :refer [project-eternalize-to eternalize]])
  (:refer-clojure :exclude [promise await]))

(defn expired? [anticipation]
  (> @nars-time (:expiry anticipation)))

(defn create-negative-confirmation-task [anticipation]
  "collected input-evidence: [w+,w-]
   anticipated evidence: [wa+,wa-]
   positive evidence lack: lack=max(0,wa+ - w+)
   evidence that was not observed: [f,c]_result = [0,  w2c(positive-lack)]
   ''justified by the amount of positive evidence that was NOT observed as anticipated to be observed''"
  (let [budget (:budget anticipation)
        anticipated-positive-evidence (:positive-evidence (t2-evidence-weights (:anticipated-truth anticipation)))
        observed-positive-evidence (:positive-evidence (t2-evidence-weights (:truth anticipation)))
        positive-evidence-lack (max 0 (- anticipated-positive-evidence
                                         observed-positive-evidence))
        confidence-of-lack (w2c positive-evidence-lack)]
    (dissoc (assoc anticipation :task-type :belief
                                :evidence (list (get-id))
                                :truth [0.0 confidence-of-lack]
                                :budget [(t-or 0.7 (nth budget 0)) (nth budget 1) (nth budget 2)]
                                :parent-statement nil
                                :occurrence (:expiry anticipation) ;could be minus/plus anticipation tolerance later
                                :terms (termlink-subterms (:statement anticipation))
                               #_:budget #_[(min 1.0
                                             (* (first (:budget anticipation))
                                                confidence-of-lack
                                                anticipation-disappointment-priority-gain))
                                        (second (:budget anticipation))
                                        (nth (:budget anticipation) 2)])
           :expiry)))

(defn create-negated-negative-confirmation-task
  [neg-confirmation]
  (assoc neg-confirmation :statement ['-- (:statement neg-confirmation)] :truth (nal.deriver.truth/negation (:truth neg-confirmation) [0.0 0.0])))

(defn confirmable-observable? [task]
  (and (:observable @state) (not= (:occurrence task) :eternal)
       (= (:source task) :derived)))

(defn create-anticipation-task [task]
  (assoc task :task-type :anticipation :expiry (let [k anticipation-scale-dependent-tolerance
                                                     scale (/ (Math/abs (- (:occurrence task) @nars-time)) k)]
                                                 (+ (:occurrence task scale))))) ;left side limit not needed since projection in revision

(defn satisfaction-based-budget-change [state belief-task goals]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence a) belief-task @nars-time)])
             (filter #(= (:statement %) (:statement belief-task)) goals))]
    (when (not-empty projected-list)
      (doseq [[goal belief-task-projected-to-goal] projected-list]
        (when (better-solution belief-task goal)
          ;update budget and solution
          ;(potential-output-answer state goal belief-task)
          (let [new-goal (reduced-goal-budget-by-belief goal belief-task-projected-to-goal)
                new-goal-with-solution (assoc new-goal :solution belief-task)]
            (update-task-in-tasks state new-goal-with-solution goal))
          (let [new-belief (increased-belief-budget-by-goal belief-task-projected-to-goal goal)]
            (update-task-in-tasks state (assoc belief-task :budget (:budget new-belief)) belief-task)))))))

(defn answer-based-budget-change [state belief-task questions]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence a) belief-task @nars-time)])
             (filter #(= (:statement %) (:statement belief-task)) questions))]
    (when (not-empty projected-list)
      (doseq [[question belief-task-projected-to-question] projected-list]
        (when (better-solution belief-task question)
          (potential-output-answer state question belief-task)
          ;update budget and solution
          (let [new-question (reduced-question-budget-by-belief question belief-task-projected-to-question)
                new-question-with-solution (assoc new-question :solution belief-task)]
            (update-task-in-tasks state new-question-with-solution question))
          (let [new-belief (increased-belief-budget-by-question belief-task-projected-to-question question)]
            (update-task-in-tasks state (assoc belief-task :budget (:budget new-belief)) belief-task)))))))

(defn revision-relevant-events [task old-event]
  (or (= (:occurrence task) :eternal)
      (< (Math/abs (- (:occurrence task) (:occurrence old-event)))
      revision-relevant-event-distance)))

(defn process-belief [state task cnt]
  ;group-by :task-type tasks
  (let [tasks (get-tasks state)
        anticipation (:anticipation @state)
        groups (group-by :task-type tasks)
        beliefs (:belief groups)]

    #_(when (and (= (:id @state) 'b) (= 0.0 (first (:truth task))))
      (println "neg." " time: " @nars-time " anticipation: " anticipation " " task))

    ;also allow revision in subterm concepts! this is why statement is compared to task statement, not to ID!!
    (when true

      (let [same-content-beliefs (filter (fn [z] (and (same-occurrence-type z task)
                                                     (= (:statement z) (:statement task)))) beliefs)]

       (let [total-revision (reduce (fn [a b] (if (and (revision-relevant-events task b)
                                                    (non-overlapping-evidence? (:evidence a) (:evidence b)))
                                                (revise a (project-eternalize-to (:occurrence a) b @nars-time))
                                                a))
                                    task (shuffle same-content-beliefs))]

         ;add revised task to bag:
         (add-to-tasks state total-revision)
         ;check if it satisfies a goal or question and change budget accordingly
         (satisfaction-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id total-revision)))) (filter #(= (:task-type %) :goal) (get-tasks state)))
         (answer-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id total-revision)))) (filter #(= (:task-type %) :question) (get-tasks state)))
         )))

    ; processing revised anticipations
    (when (and (event? task) (= (:source task) :input) (belief? task))
      (when anticipation
        (when (= (:statement anticipation) (:statement task))
          (println "here")
          (let [projected-task (project-eternalize-to (:occurrence anticipation) task @nars-time)]
            ;revise anticipation and add to tasks
            (when (non-overlapping-evidence? (:evidence projected-task) (:evidence anticipation))
               (println (str "anticipation: " anticipation "\nprojected task: " projected-task))
               (set-state! (assoc @state :anticipation (revise anticipation projected-task))))))))

    (let [anticipation (:anticipation @state)]                                                 ;be sure to use updated anticipation
      ;generate neg confirmation for expired anticipations
      ;and add to tasks
      (when (and anticipation (expired? anticipation))
       (let [neg-confirmation (create-negative-confirmation-task anticipation) ;      ;todo review budget in create-negative - currently priority of 1.0 with parents for d and q
             negated-neg-confirmation (create-negated-negative-confirmation-task neg-confirmation)]
         (println "negated neg: " negated-neg-confirmation)
         ;add neg-confirmation to tasks bag and remove anticiptaion
         (set-state! (assoc @state :anticipation nil))
         (println (str "neg conf: " neg-confirmation))
         (cast! (whereis :task-dispatcher) [:task-msg [nil nil neg-confirmation]])
         (cast! (whereis :task-dispatcher) [:task-msg [nil nil negated-neg-confirmation]]))))

    ;when task is confirmable and observabnle
    ;add an anticipation tasks to tasks
    (when (and (= (:task-type task) :belief)
            (= (:statement task)                             ;only allow anticipation with concept content
              (:id @state)))
      #_(when
        (confirmable-observable? task)
        (println (str "1. nars-time:" @nars-time
                      " 2. :task occ " (:occurrence task) " task: " (:statement task))))

      #_(when
        (and
          (not= :eternal (:occurrence task))
          (< @nars-time (:occurrence task)))
        (println (str "blbub")))
      ;(println "1")
      (when (and (confirmable-observable? task)
                 (> (:occurrence task) @nars-time))
        (println "2")
        #_(println (str "2. nars-time:" @nars-time "2. :task " task))
        (let [anticipated-task (create-anticipation-task task)
              with-anticipated-truth (fn [t] (assoc t :source :derived :anticipated-truth (:truth t) :truth [0.5 0.0]))]
          (println (str "3..."))
          (if (not= nil anticipation)
            (when (and #_(> (first (:budget anticipated-task)) (first (:budget anticipation)))
                       (< (:occurrence anticipated-task) (:occurrence anticipation)))
              (set-state! (assoc @state :anticipation (with-anticipated-truth anticipated-task))))
            (set-state! (assoc @state :anticipation (with-anticipated-truth anticipated-task))))
          (println (str "created anticipation: " (:anticipation @state))))))))
