(ns demo
  (:require
   ["https://cdn.jsdelivr.net/npm/eucalypt@0.0.3/+esm" :as r]))

(defn new-game []
  {:board (vec (repeat 9 nil))
   :x-turn? true})

(defonce state (r/atom (new-game)))

(defn winner [board]
  (let [lines [[0 1 2] [3 4 5] [6 7 8]
               [0 3 6] [1 4 7] [2 5 8]
               [0 4 8] [2 4 6]]]
    (some (fn [[a b c]]
            (when (and (get board a)
                       (= (get board a) (get board b) (get board c)))
              (get board a)))
          lines)))

(defn square [i]
  (let [{:keys [board x-turn?]} @state
        mark (get board i)]
    [:div {:style {:width "60px" :height "60px"
                   :border "1px solid #000"
                   :display "flex" :align-items "center"
                   :justify-content "center"
                   :font-size "24px"
                   :cursor "pointer"}
           :on-click #(when (and (nil? mark) (not (winner board)))
                        (swap! state update :board assoc i (if x-turn? "X" "O"))
                        (swap! state update :x-turn? not))}
     mark]))

(defn board-view []
  (let [{:keys [board]} @state]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(3, 60px)"
                   :grid-template-rows "repeat(3, 60px)"
                   :gap "2px"}}
     (for [i (range 9)]
       ^{:key i} [square i])]))

(defn game []
  (let [{:keys [board x-turn?]} @state
        w (winner board)]
    [:div {:style {:font-family "sans-serif"}}
     [board-view]
     (cond
       w [:p (str "Winner: " w)]
       (every? some? board) [:p "Draw!"]
       :else [:p (str "Next turn: " (if x-turn? "X" "O"))])
     [:button {:on-click #(reset! state (new-game))}
      "Restart"]]))

(r/render
  [game]
  (or
    (js/document.getElementById "app")
    (doto (js/document.createElement "div")
      (aset "id" "app")
      (js/document.body.prepend))))
