(ns nancy.crow.eve-wallet.esi
  "Manages ESI authenticatation: user auth, local callback server, state management" 
  (:require [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [immutant.web :as web]
            [ring.middleware.json :refer (wrap-json-response wrap-json-params)]
            [ring.middleware.keyword-params :refer (wrap-keyword-params)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.util.response :refer (response)]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [taoensso.timbre :as log]
            [java-time :as jt]))

;; # Authentication (defprotocol)
;; 
;; This protocol defines the "public interface" for the ESI auth component. 
;; Exposes functionality to set and refresh access token and fetch character id.
(defprotocol EsiAuthProto
  (update-access-token  [this new-token])
  (refresh-access-token [this])
  (get-char-id          [this]))

(defn oauth-token
  "Call the oauth access token"
  [client secret body]
  (-> (http/post "https://login.eveonline.com/oauth/token"
                 {:basic-auth [client secret]
                  :content-type :json
                  :body body})
      :body
      (json/parse-string true)
      (select-keys [:access_token
                    :refresh_token
                    :expires_in])
      (assoc :timestamp (jt/instant))))

(defn- oauth-access-token
  [client secret auth-code]
  (->> {:grant_type :authorization_code
        :code       auth-code}
       (json/generate-string)
       (oauth-token client secret)))

(defn- oauth-refresh-token
  [client secret refresh-token]
  (->> {:grant_type :refresh_token
        :refresh_token refresh-token}
       (json/generate-string)
       (oauth-token client secret)))

(defn- oauth-handler
  [request]
  (let [auth                    (:auth request)
        auth-code               (get-in request [:query-params "code"])
        {:keys [client secret]} (:config auth)]
    (->> (oauth-access-token client secret auth-code)            
         (update-access-token auth))
    (response "Success")))

(defroutes app
  (GET "/oauth-callback" [] oauth-handler)
  (route/not-found "Route not found"))

(defn- wrap-auth-request
  [handler auth]
  (fn [req]
    (handler (assoc req :auth auth))))

;; Auth component
;; hold state for callback http server, and current token to pull data

(defrecord EsiAuth
           [config server token]

  component/Lifecycle
  (start [this]
    (if-not server
      (do
        (log/info "Starting immutant callback server")
        (assoc this :server
               (web/run (-> app
                            (wrap-auth-request this)
                            (wrap-json-response)
                            (wrap-keyword-params)
                            (wrap-json-params)
                            (wrap-params))
                        (:server config))))
      (do
        (log/warn "Server already running")
        this)))

  (stop [this]
    (if server
      (do
        (web/stop server)
        (assoc this :server nil))
      (log/warn "Immutant callback server not running"))
    (reset! token {}))
  
  EsiAuthProto
  (update-access-token [_ new-token]
    (swap! token merge new-token))
  
  (refresh-access-token [_]
    (let [{:keys [client secret]} config]
      (-> (:refresh_token @token)
          (oauth-refresh-token client secret)
          (swap! token merge (oauth-refresh-token client secret)))
      true))
  
  (get-char-id [this]
    @token))

(defn auth-request
  "Opens a browser window with an oath login, our server should pick up the redirect with the user's code"
  [auth state]
  (let [{:keys [scopes client redirect-url]} (:config auth)
        url (str "https://login.eveonline.com/oauth/authorize"
                 "?response_type=code"
                 "&redirect_uri=" redirect-url
                 "&client_id=" client
                 "&scope=" scopes
                 (when state
                   (str "&state=" state)))]
    url))

(defn default-config
  []
  {:client       "<replace me>"
   :secret       "<replace me and keep secret>"
   :scopes       "esi-wallet.read_character_wallet.v1 esi-wallet.read_corporation_wallets.v1"
   :redirect-url "http://localhost/oauth-callback"
   :server       {:host "0.0.0.0"
                  :port 80}})

(defn create-auth
  ([]
   (create-auth (default-config)))
  ([config]
   (map->EsiAuth {:config config
                  :server nil
                  :token  (atom {})})))