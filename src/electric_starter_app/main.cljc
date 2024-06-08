(ns electric-starter-app.main
  (:require [hyperfiddle.electric :as e]
            [contrib.str :refer [blank->nil]]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            ))

; Constants
(def DECK [0 1 2 3 5 8])

; State
#?(:clj (defonce !db (atom {})))
(e/def db (e/server (e/watch !db)))

; Actions
(defn join! [db session-id username]
  (swap! db assoc session-id username))

(defn leave! [db session-id]
  (swap! db dissoc session-id))

; Queries
(defn active-player? [current-session-id session-id]
  (= current-session-id session-id))

; UI
(e/defn FullDeck []
  (e/client
    (dom/div
      (e/for [card DECK]
        (dom/span 
          (dom/text card)
          (dom/props
            {:style {:border "1px solid black"
                     :padding "5px"
                     :margin "5px"
                     :font-size "20px"
                     :display "inline-block"
                     :cursor "pointer"
                     :background-color "white"
                     }}
          ))))))

(e/defn DeckCover []
  (e/client
    (dom/div
      (dom/span 
        (dom/text "X")
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
                       (dom/text "Leave")))
          (dom/div
            (dom/ul
              (e/server 
                (e/for-by identity [player db]
                          (e/client 
                            (dom/li (dom/text player) 
                                    (dom/div (if (active-player? session-id (first player)) 
                                                (FullDeck.)
                                                (DeckCover.))))
                            )))))
          )))))

(e/defn Main [ring-request]
  (e/server 
    (binding [e/http-request ring-request]
      (e/client
        (binding [dom/node js/document.body]
          (App.)
    )))))
