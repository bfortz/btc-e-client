(ns btc-e-client.core
  "Client library for accessing the wex (formerly btc-e) api.
  Needs your secret and key inside environment variables called BTC_KEY and BTC_SECRET"
  (:use pandect.algo.sha512)
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.string :as s]
            [clojure.walk]))

(defn init
  "Initiates an api structure which can be used to interact with the api.
  Currency is the currencies you want to interact in, for example :btc_usd,
  :ltc-usd, etc... You can leave the secret and api-key as empty strings if you
  just want to use the public api"
  [currency secret api-key ]
  { :curr currency :secret secret :key api-key})

(def default-api (init :btc_usd "" ""))

(def endpoint "https://wex.fit/tapi")

(defn- public-api
  ([endpt]
   (str "https://wex.fit/api/3/" (name endpt))) 
  ([api endpt]
   (str "https://wex.fit/api/3/" (name endpt) "/" (name (:curr api)))))

(defn- get-body-sync [url]
  (json/read-str (:body @(http/get url {:timeout 2000})) :key-fn keyword))

;(throw (Throwable. (str "Request for " url " Failed.")))
(defn get-body-async [url]
  (let [p (promise)] 
    (http/get url  #(if-let [body (:body %)]
                      (deliver p (json/read-str body :key-fn keyword))
                      (deliver p %))) ;; error
    p)) 

(defn get-ticker
  ([] (get-ticker default-api))
  ([api] (get-body-sync (public-api api :ticker)))
  ([api async] (get-body-async (public-api api :ticker))))

(defn get-trades
  ([] (get-trades default-api))
  ([api] (get-body-sync (public-api api :trades)))
  ([api async] (get-body-async (public-api api :trades))))

(defn get-info
  ([] (get-body-sync (public-api :info)))
  ([async] (get-body-sync (public-api :info))))

;; Deprecated - use get-info instead
;;(defn get-fee
;;  ([] (get-fee default-api))
;;  ([api] (get-body-sync (public-api api :fee)))
;;  ([api async] (get-body-async (public-api api :fee))))

(defn get-depth
  ([] (get-depth default-api))
  ([api] (get-body-sync (public-api api :depth)))
  ([api async] (get-body-async (public-api api :depth))))


;; async public apis

(defn get-ticker-async
  ([] (get-ticker-async default-api))
  ([api] (get-body-async (public-api api :ticker))))

(defn get-trades-async
  ([] (get-trades-async default-api))
  ([api] (get-body-async (public-api api :trades))))

;; Deprecated - use get-info instead
;; (defn get-fee-async
;;   ([] (get-fee-async default-api))
;;   ([api] (get-body-async (public-api api :fee))))

(defn get-depth-async
  ([] (get-depth-async default-api))
  ([api] (get-body-async (public-api api :depth))))



;; This is how their api works

;; HTTP headers:
;; key : api-key
;; sign : the HMAC sig: post data signed by secret using HMAC-SHA512

;; Endpoint
;; https://wex.fit/tapi

;; POST Data
;; nonce : a nonce, using the unix ts in ms
;; method : method name
;; param : val
;; param2 : val2
;; ...

(defn remove-zeros
  "Remove trailing zeros except after a dot"
  [x]
  (if (s/ends-with? x ".0") 
    x
    (if (s/ends-with? x "0") 
      (recur (subs x 0 (dec (count x)))) 
      x)))
  
(defn mystr
  "Make sure clojure does not switch to E notation"
  [x]
  (if (number? x) (remove-zeros (format "%f" x)) x))

(defn- postage
  "Creates a post body"
  [param-map]
  (->> param-map
       (clojure.walk/stringify-keys)
       (reduce #(str %1 "&" (first %2) "=" (mystr (second %2))) "")
       (#(subs % 1)))) ;; Get rid of the first "&" in "&param1=stuff"

(defn- btc-e-request-raw [api post-data]
  ;; add the nonce to the data
  (let [post-data (str post-data "&nonce=" (int (/ (System/currentTimeMillis) 1000)))]
    (http/post endpoint {:headers {"Key" (:key api)
                                   "Sign" (sha512-hmac post-data (:secret api))
                                   "Content-Type" "application/x-www-form-urlencoded"}
                         :body post-data})))

(defn async-trade-api-request
  "Call the btc-e api"
  ([api method-name params]
   (btc-e-request-raw api (postage (assoc params "method" method-name))))
  ([api method-name]
   (async-trade-api-request api method-name {})))

(defn trade-api-request
  "Synchronous version to call the api request"
  ([api method-name params]
   (json/read-str
     (:body @(async-trade-api-request api method-name params))
     :key-fn keyword))
  ([api method-name]
   (trade-api-request api method-name {})))

;; Demo
(comment

  ;; load the library
  (require '[btc-e-client.core :as btce])

  (def api (btce/init :btc_usd "mysecret" "mykey"))

  (btce/get-ticker api) ;; Get the ticker
  (btce/get-ticker) ;; By default public api methods use btc_usd

  (get-in (btce/get-ticker) [:ticker :avg]) ;; Get the average

  (btce/get-trades) ;; get the trades

  ;; The trade api's method names and params can be found at
  ;; https://wex.fit/api/documentation
  ;;
  ;; trade-api-request and async-trade-api-request must take in an api, since
  ;; they need the api key/secret

  ;; Play around with the trade api
  (btce/trade-api-request api "TradeHistory")

  ;; Get the user's Info
  (btce/trade-api-request api "getInfo")

  ;; Cancel an order with an argument
  (btce/trade-api-request api "CancelOrder" {:order_id 123})

  )


