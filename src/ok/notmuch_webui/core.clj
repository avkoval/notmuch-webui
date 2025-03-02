(ns ok.notmuch-webui.core
  (:require 
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   )
  (:gen-class))


(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello World"})

(defn handler2 [_]
  {:status 200, :body "ok12"})

(def app
  (ring/ring-handler
    (ring/router 
     [["/" {:get handler2}]
      ["/hello" {:get handler}]
      ["/assets/*" (ring/create-resource-handler)]
      ])))


(def router
  (ring/router
    ["/ping" {:get handler2}]))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (jetty/run-jetty
           (-> #'app 
               wrap-reload
               )
           {:port 8080 :join? false})))

(def nrepl-port 7888)

(defn start-nrepl []
  (println (str "Starting nrepl-server on port " nrepl-port))
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn run
  [& args]
  )

(defn -main
  [& args]
  (start!)
  (start-nrepl)
  )
