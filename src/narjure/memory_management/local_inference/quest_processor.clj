(ns narjure.memory-management.local-inference.quest-processor
  (:require
    [co.paralleluniverse.pulsar.actors
     :refer [! spawn gen-server register! cast! Server self
             shutdown! unregister! set-state! state whereis]]
    [narjure.actor.utils :refer [defactor]]
    [taoensso.timbre :refer [debug info]]
    [narjure.bag :as b]
    [narjure.debug-util :refer :all]
    [narjure.control-utils :refer :all]
    [narjure.global-atoms :refer :all]
    [narjure.memory-management.local-inference.local-inference-utils :refer :all]
    [nal.deriver.truth :refer [t-or confidence frequency]]
    [nal.deriver.projection-eternalization :refer [project-eternalize-to]])
  (:refer-clojure :exclude [promise await]))

(defn user? [task]
  (= (:source task) :input))

(defn process-quest [state quest tasks]
  ;group-by :task-type tasks
  (let [goals (filter #(= (:task-type %) :goal) tasks)]
    (info (str "task bag: " tasks))
    ;filter beliefs matching concept content
    ;project to task time
    ;select best ranked
    (let [projected-goal-tuples (map (fn [a] [a (project-eternalize-to (:occurrence quest) a @nars-time)]) (filter #(= (:statement %) (:statement quest)) goals))]
      (if (not-empty projected-goal-tuples)
        ;select best solution
        (let [[goal projected-goal] (apply max-key (fn [a] (confidence (second a))) projected-goal-tuples)
              answerered-quest (assoc quest :solution goal)]
          ;(info (str "at if)"))
          (if (or (= (:solution quest) nil)
                  (> (second (:truth projected-goal))
                     (second (:truth (project-eternalize-to (:occurrence quest) (:solution quest) @nars-time)))))
            ;update budget and tasks
            (let [result (decrease-quest-budget-by-solution answerered-quest)]
              (update-task-in-tasks state result quest)

              ;Update goal also:
              (let [new-goal (increased-goal-budget-by-quest projected-goal quest)]
                (update-task-in-tasks state new-goal goal))

              ;if answer to user quest ouput answer
              (when (and (user? quest)
                         (= (:statement quest) (:id @state)))
                ;(info (str "result: " result))
                (output-task [:answer-to (str (narsese-print (:statement quest)) "@")] (:solution result))))

            (add-to-tasks state quest)        ;it was not better, we just add the question and dont replace the solution
            ))
        ;was empty so just add
        (add-to-tasks state quest)))))
