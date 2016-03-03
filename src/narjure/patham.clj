(ns patham
  (:require [instaparse.core :as insta
             ])
  (:gen-class))

;truth expectation, w2c, eternalization
(defn expectation [t]
    (+ (* (:confidence t) (- (:frequency t) 0.5)) 0.5))

(defn w2c [w] (/ w (+ w 1)))

(defn eternalize [t]
  (assoc t :confidence (w2c (:confidence t))
           :occurence -1))

;whether the task has a question variable
(defn has-question-var [ref] false)

;ranking function, confidence for y/n, expectation for wh-tasks
(defn rank-value [ref t]
  (if (has-question-var ref)
    (:confidence t)
    (expectation t)))

;confidence factor when projecting source to target time
(defn project [t ref curtime]
  (let [sourcetime (:occurence t)
        targettime (:occurence ref)
        dist (defn dist [a b] (Math/abs (- a b)))]
    (assoc t
     :confidence (* (:confidence t)
                    (/ (dist sourcetime targettime)
                       (+ (dist sourcetime curtime)
                          (dist targettime curtime))))
     :occurence targettime)))

;the confidence a task has after projection to ref time
(defn project-eternalize [t ref curtime]
  (let [sourcetime (:occurence t)
        targettime (:occurence ref)]
    (cond (and (=    targettime -1) (=    sourcetime -1)) t
          (and (not= targettime -1) (=    sourcetime -1)) t
          (and (=    targettime -1) (not= sourcetime -1)) (eternalize t)
          :else (let [tEternal (eternalize t)
                      tProjected (project t targettime curtime)]
                  (if (> (:confidence tEternal) (:confidence tProjected))
                    tEternal tProjected))))
  )

;rank a task according to a reference
(defn rank-task [ref curtime t]
  {:task t
   :value (rank-value ref (project-eternalize t ref curtime))})

;get the best ranked table entry when ranked according to ref
(defn best-ranked [table ref curtime]
  (apply max-key :value
         (map (partial rank-task ref curtime) table)))

;add belief to a table
(defn add-to-table [concept table x]
  (assoc concept table
                 (conj (concept table) x)))

;add to belief table
(defn add-to-beliefs [concept x]
  (add-to-table concept :beliefs x))

;add to desires table
(defn add-to-desires [concept x]
  (add-to-table concept :desires x))

;Concept data structure
(defn buildConcept [name]
  {:term name :beliefs [] :desires [] :tasklinks [] :termlinks []})

; (let [concept (buildConcept "test")]
;  (add-to-beliefs concept {:term "tim --> cat" :frequency 1 :confidence 0.75 :occurrence 10}))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ((insta/parser (clojure.java.io/resource "narsese.bnf") :auto-whitespace :standard) "<bird --> swimmer>. %0.10;0.60%"))
