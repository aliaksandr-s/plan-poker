(ns electric-starter-app.main
  (:require [hyperfiddle.electric :as e]
            [contrib.str :refer [blank->nil]]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            #?(:cljs ["js-cookie" :as js-cookie])
            ))

; Constants
(def DECK [1 2 3 5 8])
(def USERNAME_COOKIE "username")

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
  (swap! db (fn [db]
              (-> db
                  (assoc :cards-revealed false)
                  (update :players (fn [players] (into {} (map (fn [[k v]] [k (assoc v :picked-card nil)]) players))))))))

(defn reveal-cards! [db]
  (swap! db assoc :cards-revealed true))

#?(:cljs 
   (defn persist-username! [username] 
     (.set js-cookie USERNAME_COOKIE username)))

#?(:cljs 
   (defn delete-persisted-username! [] 
     (.remove js-cookie USERNAME_COOKIE)))

; Queries
(defn players [db] (-> db :players))
(defn player-id [player] (first player))

(defn picked-card [db session-id]
  (-> db :players (get session-id) :picked-card))

(defn active-player? [current-session-id session-id]
  (= current-session-id session-id))

(defn card-picked? [db session-id]
  (-> db :players (get session-id) :picked-card (not= nil)))

(defn cards-revealed? [db]
  (-> db :cards-revealed))

#?(:cljs
   (defn persisted-username [] 
     (.get js-cookie USERNAME_COOKIE)))

; UI
(e/defn FullDeck [session-id]
  (e/client
    (dom/div
      (dom/props {:style {:display "flex" :gap "8px" :flex-wrap "wrap"}})
      (e/for [val DECK]
        (ui/button
          (e/fn [] (e/server (pick-card! !db session-id val)))
          (dom/text val)
          (dom/props
            {:class ["card" 
                     (if (= (picked-card db session-id) val) "active" nil)]}
            ))))))

(e/defn SingleCard [neighbour-session-id]
  (e/client
    (let [card-open? (and (cards-revealed? db) (card-picked? db neighbour-session-id))]
      (dom/div
        (dom/props 
          {:class ["card" 
                   (cond 
                     card-open?                                   "open"
                     (card-picked? db neighbour-session-id)       "picked"
                     :else                                        "unpicked")
                   ]})
        (dom/text (when card-open? (picked-card db neighbour-session-id)))))))

(e/defn App []
  (e/client
    (let [session-id (e/server (get-in e/http-request [:headers "sec-websocket-key"]))
          !username  (atom "")
          username   (e/watch !username)]
      (e/server 
        (e/on-unmount #(leave! !db session-id))
        (e/client 
          (when-let [usr (persisted-username)]
            (e/server (join! !db session-id usr)))
          (dom/div 
            (dom/br)
            (ui/input username 
                      (e/fn [v] (reset! !username v))
                      (dom/props {:class ["input"]}))
            (dom/br)
            (dom/br)
            (ui/button (e/fn [] 
                         (when 
                           (e/server (join! !db session-id username))
                           (e/client (reset! !username "")
                                     (persist-username! username))))
                       (dom/text "Join")
                       (dom/props 
                         {:class ["btn"]
                          :disabled (nil? (blank->nil username))}))
            (ui/button (e/fn [] 
                         (when
                           (e/server (leave! !db session-id))
                           (e/client (reset! !username "")
                                     (delete-persisted-username!))))
                       (dom/text "Leave")
                       (dom/props {:class ["btn"]}))
            (dom/br)
            (dom/br)
            (ui/button (e/fn [] (e/server (reset-picked-cards! !db)))
                       (dom/text "Reset")
                       (dom/props {:class ["btn"]}))
            (ui/button (e/fn [] (e/server (reveal-cards! !db)))
                       (dom/text "Reveal")
                       (dom/props {:class ["btn"]}))
            )
          (dom/div
            (dom/ul
              (e/server 
                (e/for-by player-id [player (players db)]
                          (e/client 
                            (dom/li (dom/text player) 
                                    (dom/div (if (active-player? session-id (player-id player)) 
                                                (FullDeck. session-id)
                                                (SingleCard. (player-id player)))))
                            )))))
          )))))

(e/defn Main [ring-request]
  (e/server 
    (binding [e/http-request ring-request]
      (e/client
        (binding [dom/node js/document.body]
          (App.)
    )))))
