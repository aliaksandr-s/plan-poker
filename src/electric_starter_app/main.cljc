(ns electric-starter-app.main
  (:require [hyperfiddle.electric :as e]
            [contrib.str :refer [blank->nil]]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            ))

; Constants
(def DECK [0 1 2 3 5 8])

; State
; players {"session-id" {:username "username" :picked-card nil} "session-id" {:username "username" :picked-card nil}}
#?(:clj (defonce !db (atom {:players {} 
                            :cards-revealed false})))
(e/def db (e/server (e/watch !db)))

; Actions
(defn join! [db session-id username]
  (swap! db update :players assoc session-id {:username username :picked-card nil}))

(defn leave! [db session-id]
  (swap! db update :players dissoc session-id))

(defn pick-card! [db session-id card]
  (swap! db update :players assoc-in [session-id :picked-card] card))

(defn reset-picked-cards! [db]
  (swap! db update :players (fn [db] (into {} (map (fn [[k v]] [k (assoc v :picked-card nil)]) db)))))

; Queries
(defn players [db] (-> db :players))
(defn player-id [player] (first player))

(defn picked-card [db session-id]
  (-> db :players (get session-id) :picked-card))

(defn active-player? [current-session-id session-id]
  (= current-session-id session-id))

(defn card-picked? [db session-id]
  (-> db :players (get session-id) :picked-card (not= nil)))

; UI
(e/defn FullDeck [session-id]
  (e/client
    (dom/div
      (e/for [val DECK]
        (ui/button
          (e/fn [] (e/server (pick-card! !db session-id val)))
          (dom/text val)
          (dom/props
            {:style {:border "1px solid black"
                     :padding "4px"
                     :margin "4px"
                     :font-size "20px"
                     :display "inline-block"
                     :cursor "pointer"
                     :background-color (if (= (picked-card db session-id) val) "lightgray" "white")}}
            ))))))

(e/defn DeckCover [neighbour-session-id]
  (e/client
    (dom/div
      (dom/span 
        (dom/text (if (card-picked? db neighbour-session-id) "O" "X"))
        (dom/props
          {:style {:border "1px solid black"
                   :padding "5px"
                   :margin "5px"
                   :font-size "20px"
                   :display "inline-block"
                   :cursor "pointer"
                   :background-color "white"}}
          )))))

(e/defn App []
  (e/client
    (let [session-id (e/server (get-in e/http-request [:headers "sec-websocket-key"]))
          !username  (atom "")
          username   (e/watch !username)]
      (e/server 
        (e/on-unmount #(leave! !db session-id))
        (e/client 
          (dom/div 
            (ui/input username (e/fn [v] (reset! !username v)))
            (ui/button (e/fn [] 
                         (when 
                           (e/server (join! !db session-id username))
                           (e/client (reset! !username ""))))
                       (dom/text "Join")
                       (dom/props {:disabled (nil? (blank->nil username))}))
            (ui/button (e/fn [] (e/server (leave! !db session-id)))
                       (dom/text "Leave"))
            (dom/br)
            (dom/br)
            (ui/button (e/fn [] (e/server (reset-picked-cards! !db)))
                       (dom/text "Reset"))
            (ui/button (e/fn [] (println db))
                       (dom/text "Reveal"))
            )
          (dom/div
            (dom/ul
              (e/server 
                (e/for-by player-id [player (players  db)]
                          (e/client 
                            (dom/li (dom/text player) 
                                    (dom/div (if (active-player? session-id (player-id player)) 
                                                (FullDeck. session-id)
                                                (DeckCover. (player-id player)))))
                            )))))
          )))))

(e/defn Main [ring-request]
  (e/server 
    (binding [e/http-request ring-request]
      (e/client
        (binding [dom/node js/document.body]
          (App.)
    )))))
