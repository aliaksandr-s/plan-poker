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
; cards-revealed false
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
(defn players-without-current [db session-id] (->> (players db) (filter #(not= session-id (first %)))))

(defn player [db session-id] 
  (when-let [player (get-in db [:players session-id])] 
    [session-id player]))
(defn player? [db session-id] (-> (player db session-id) (not= nil)))

(defn player-id [player] (first player))
(defn player-name [player] (-> player second :username))
(defn picked-card [player] (-> player second :picked-card))
(defn card-picked? [player] (-> (picked-card player) (not= nil)))

(defn cards-revealed? [db]
  (-> db :cards-revealed))

#?(:cljs
   (defn persisted-username [] 
     (.get js-cookie USERNAME_COOKIE)))

; UI
(e/defn Hand [session-id]
  (e/client
    (dom/div
      (dom/props {:class ["hand"]})
      (e/for [val DECK]
        (ui/button
          (e/fn [] (e/server (pick-card! !db session-id val)))
          (dom/text val)
          (dom/props
            {:class ["hand__card" 
                     (if (= (-> (player db session-id) picked-card) 
                            val) 
                       "hand__card--active" 
                       nil)]}
            ))))))

(e/defn Player [player pos]
  "pos => :top | :left | :right | :bottom"
  (e/client
    (dom/div
      (dom/props {:class ["player" 
                          (case pos
                            :top    "player--top"
                            :left   "player--left"
                            :right  "player--right"
                            :bottom "player--bottom")]})
      (dom/div 
        (dom/props {:class ["player__name"]})
        (dom/text (player-name player)))
      (let [card-open? (and (cards-revealed? db) (card-picked? player))]
        (dom/div
          (dom/props 
            {:class ["card" 
                     (cond 
                       card-open?            nil
                       (card-picked? player) "card--picked"
                       :else                 "card--unpicked")
                     ]})
          (dom/text (when card-open? (picked-card player))))))))

(e/defn JoinComponent [session-id]
  (e/client
    (let [dialog-id "join-dialog" 
          !username (atom "")
          username  (e/watch !username)]
      (dom/div
        (ui/button
          (e/fn [] (.showModal (js/document.querySelector (str "#" dialog-id))))
          (dom/props {:class ["btn"]})
          (dom/text "Join"))
        (dom/dialog 
          (dom/props {:open false :id dialog-id})
          (dom/header 
            (dom/h3 (dom/text "Join the Game"))
            (ui/button
              (e/fn [] (.close (js/document.querySelector (str "#" dialog-id))))
              (dom/props {:class ["dialog__close"]})))
          (dom/section 
            (dom/label (dom/text "Name") (dom/props {:for "username"}))
            (ui/input username 
                      (e/fn [v] (reset! !username v))
                      (dom/props {:class ["input"] :id "username"})))
          (dom/footer (ui/button (e/fn [] 
                                   (when 
                                     (e/server (join! !db session-id username))
                                     (e/client (reset! !username "")
                                               (persist-username! username)
                                               (.close (js/document.querySelector (str "#" dialog-id))))))
                                 (dom/text "Join")
                                 (dom/props 
                                   {:class ["btn"]
                                    :disabled (nil? (blank->nil username))}))))))))

(e/defn App []
  (e/client
    (let [session-id (e/server (get-in e/http-request [:headers "sec-websocket-key"]))]
      (e/server 
        (e/on-unmount #(leave! !db session-id))
        (e/client 
          (when-let [usr (persisted-username)]
            (e/server (join! !db session-id usr)))
          (dom/div (dom/props {:class ["container"]})
            (dom/div 
              (dom/props {:class ["topbar"]})
              (dom/div 
                (dom/props {:class ["menu"]})
                (dom/button 
                  (dom/props {:class ["btn menu__button"]})
                  (dom/text ""))
                (dom/nav
                  (dom/props {:class ["menu__list"]})
                  (ui/button (e/fn [] 
                               (when
                                 (e/server (leave! !db session-id))
                                 (e/client (delete-persisted-username!))))
                             (dom/text "Leave")
                             (dom/props {:class ["btn"]})))))
            (dom/div
              (dom/props {:class ["top-row"]})
              (dom/div (dom/props {:style {:display "flex" :gap "12px" :justify-content "center" :align-items "center"}})
                       (e/server 
                         (e/for-by player-id [player (players-without-current db session-id)]
                                   (e/client 
                                     (dom/div (dom/div
                                                (dom/props {:style {:display "flex" :flex-direction "column"}})
                                                (Player. player :top))))))))
            (dom/div 
              (dom/props {:class ["center-row"]})
              (dom/div
                (dom/props {:class ["table"] :style {:width (str (* (count (players db)) 76) "px")}})
                (if-not (player? db session-id) 
                  (JoinComponent. session-id)
                  (dom/div 
                    (when (cards-revealed? db)
                      (ui/button (e/fn [] (e/server (reset-picked-cards! !db)))
                                 (dom/text "Reset")
                                 (dom/props {:class ["btn"]})))
                    (when-not (cards-revealed? db)
                      (ui/button (e/fn [] (e/server (reveal-cards! !db)))
                                 (dom/text "Reveal")
                                 (dom/props {:class ["btn"]})))))))
            (dom/div
              (dom/props {:class ["bottom-row"]})
              (if (player? db session-id)
                (Player. (player db session-id) :bottom)
                (dom/div (dom/props {:class ["card card--unpicked"]}))))
            (when (player? db session-id) 
              (Hand. session-id)))
          )))))

(e/defn Main [ring-request]
  (e/server 
    (binding [e/http-request ring-request]
      (e/client
        (binding [dom/node js/document.body]
          (App.)
    )))))
