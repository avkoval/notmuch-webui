(ns ok.notmuch-webui.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [selmer.parser :refer [render-file] :as selmer]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response]]
   [ok.notmuch-webui.notmuch :as notmuch]
   [ok.notmuch-webui.utils :as utils]
   [clojure.data.json :as json]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.string :as string]
   )
  (:gen-class))


(defn paginator [total limit query page]
  (let [pages (/ total limit)
        pages-count (int (if (ratio? pages) (+ 1 pages) pages))
        between-current-and-end (+ page (int (/ (- pages-count page) 2)))]
    {:total total
     :current-page page
     :limit limit
     :pages pages-count
     :previous-page (if (>= (- page 1) 1) (- page 1) nil)
     :next-page (if (<= (+ page 1) pages-count) (+ page 1) nil)
     :between-current-and-start (if (> (/ page 2) 2) (int (/ page 2)) nil)
     :between-current-and-end (if (> (- pages between-current-and-end) 2) between-current-and-end nil)
     })
  )


(defn home [request]
  (let [query-params (utils/decode-form-params (:query-string request))
        query (or (:query query-params) "tag:inbox")
        search-results-count (notmuch/search-results-count query {})
        limit (get notmuch/default-search-options "--limit")
        page (utils/parse-number-alt (get query-params :page) 1)
        paginator (paginator search-results-count limit query page)
        offset (* (:current-page paginator) limit)
        search-results (notmuch/search query {"--offset" offset})
        ]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/home.html" {:search-results search-results
                                               :paginator paginator
                                               :search-query query})}))

(defn sanitize-query 
  "TODO: escape shell arguments too, remove duplicate tags"
  [q]
  (-> q
      (string/replace #"^\s*and\s" "")
      (string/trim)
      )
  )

(defn notmuch-search [request]
  ;; Create a SSE response
  (->sse-response request
   {:on-open
    (fn [sse]
      ;; Merge html fragments into the DOM
      (let [query (sanitize-query (or (get-in request [:json :searchQuery]) "tag:inbox"))
            ;; search-results )
            currentPage (get-in request [:json :currentPage] 1)
            search-results-count (or (notmuch/search-results-count query {}) 0)
            limit (get notmuch/default-search-options "--limit")
            paginator (paginator search-results-count limit query currentPage)
            offset (* (- currentPage 1) limit)
            search-results (if (nil? query) {} (notmuch/search query {"--offset" offset}))
            ]
        (utils/pprint (:json request))
        (cond
          (not (nil? (:messages search-results)))
          (do 
            (d*/merge-fragment! sse
                                (render-file
                                 "templates/search-results-table.html" {:search-results search-results
                                                                        :paginator paginator
                                                                        :search-query query}))
            (d*/merge-signals! sse "{loading: false}")
            (d*/merge-signals! sse (str "{searchQuery: \"" query " \"}"))
            ))))}))

(def app
  (ring/ring-handler
    (ring/router
     [["/" {:get home}]
      ["/assets/*" (ring/create-resource-handler)]
      ["/notmuch-search" {:post notmuch-search}]
      ])
    (constantly {:status 404, :body "Not Found."})))


(defonce server (atom nil))

(defn wrap-json-params
  "Add :json to request map when content-type is application/json"
  [handler]
  (fn [request]
    (let [body (slurp (:body request))]
      (if (= "application/json" (:content-type request))
        (handler (assoc request :json (keywordize-keys (json/read-str body))))
        (handler request)
        )
      )))

(defn start! []
  (reset! server
          (jetty/run-jetty
           (-> #'app
               wrap-reload
               wrap-params
               wrap-json-params
               )
           {:port 8080 :join? false})))

(def nrepl-port 7888)

(defn start-nrepl []
  (println (str "Starting nrepl-server on port: " nrepl-port))
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))


(defn run [& args]
  (start!))

(defn -main [& args]
  (start!)
  (selmer.parser/cache-off!)
  (start-nrepl))

(comment
  (sanitize-query "and tag:replace ")
)
