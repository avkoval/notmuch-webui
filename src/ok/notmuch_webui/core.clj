(ns ok.notmuch-webui.core
  (:require 
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [selmer.parser :refer [render-file]]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response]]
   )
  (:gen-class))


(defn home [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (render-file "templates/home.html" {})})

(defn handler2 [_]
  {:status 200, :body "ok12"})


(defn sse-test [request]
  ;; Create a SSE response
  (println "ook-2025-02-25-1740466060")
  (->sse-response request
   {:on-open
    (fn [sse]
      ;; Merge html fragments into the DOM
      (d*/merge-fragment! sse
        "<div id=\"question\">What do you put in a toaster?</div>")

      ;; Merge signals into the signals
      ;; (d*/merge-signals! sse "{response: '', answer: 'bread'}")
      )}))


(def app
  (ring/ring-handler
    (ring/router 
     [["/" {:get home}]
      ["/assets/*" (ring/create-resource-handler)]
      ["/sse-test" {:get sse-test}]
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
