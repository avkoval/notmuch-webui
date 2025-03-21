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


(defn paginate [total limit query page options]
  (let [pages (/ total limit)
        count (int (if (ratio? pages) (+ 1 pages) pages))
        pages-count (if (> count 0) count 1)
        current-page (if (> page pages-count) pages-count page)
        between-current-and-end (+ current-page (int (/ (- pages-count current-page) 2)))]
    {:total total
     :pages pages-count
     :current-page current-page
     :default-limit (or (:limit options) limit)
     :current-limit limit
     :previous-page (if (>= (- current-page 1) 1) (- page 1) nil)
     :next-page (if (<= (+ current-page 1) pages-count) (+ page 1) nil)
     :between-current-and-start (if (> (/ current-page 2) 2) (int (/ page 2)) nil)
     :between-current-and-end (if (> (- pages between-current-and-end) 2) between-current-and-end nil)
     })
  )


(defn home [request]
  (let [query-params (utils/decode-form-params (:query-string request))
        query (or (:query query-params) "tag:inbox")
        limit (get notmuch/default-search-options "--limit")
        page (utils/parse-number-alt (get query-params :page) 1)
        offset (* page limit)
        search-results (notmuch/search query {"--offset" offset})
        ]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/home.html" {:search-results search-results
                                               :page page
                                               :paginator nil
                                               :search-query query})}))


(defn sanitize-query 
  "TODO: escape shell arguments too, remove duplicate tags"
  [q]
  (-> q
      (string/replace #"^\s*and\s" "")
      (string/trim)
      )
  )

(defn show [request]
  (let [query-params (utils/decode-form-params (:query-string request))
        query (or (:query query-params) "thread:not-found")
        search-results-count (notmuch/search-results-count query {})
        messages (notmuch/show query {})
        ]
    ;; (utils/pprint messages)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/show.html" {:messages messages
                                               :count search-results-count
                                               :search-query query})}))



(defn paginator [request]
  (->sse-response request
   {:on-open
    (fn [sse]
      ;; Merge html fragments into the DOM
      (let [
            query (sanitize-query (or (get-in request [:query-params "datastar" "searchQuery"]) "tag:inbox"))
            currentPage (get-in request [:json :currentPage] 1)
            search-results-count (or (notmuch/search-results-count-cached query {}) 0)
            default-limit (get notmuch/default-search-options "--limit")
            limit (or (:currentLimit query) default-limit)
            paginator (paginate search-results-count limit query currentPage {:limit default-limit})
            ]
        (utils/pprint paginator)
        (d*/merge-fragment! sse
                                (render-file
                                 "templates/paginator.html" {:paginator paginator
                                                             :search-query query}))
        ))}))


(defn notmuch-search [request]
  (->sse-response request
   {:on-open
    (fn [sse]
      ;; Merge html fragments into the DOM
      (let [query (sanitize-query (or (get-in request [:json :searchQuery]) "tag:inbox"))
            ;; search-results )
            currentPage (get-in request [:json :currentPage] 1)
            search-results-count (or (notmuch/search-results-count-cached query {}) 0)
            default-limit (get notmuch/default-search-options "--limit")
            currentLimit (get-in request [:json :currentLimit] default-limit)
            limit (if (>= currentLimit default-limit) currentLimit default-limit)
            paginator (paginate search-results-count limit query currentPage {:limit default-limit})
            offset (* (- currentPage 1) limit)
            search-results (if (nil? query) {} (notmuch/search query {"--offset" offset "--limit" limit}))
            ]
        (utils/pprint (:json request))
        (utils/pprint paginator)
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


(selmer/add-tag! :pprint
  (fn [args context-map]
    (let [varname (first args)
          value (get context-map (keyword varname))]
      (with-out-str (utils/pprint value)))))

(def app
  (ring/ring-handler
    (ring/router
     [["/" {:get home}]
      ["/assets/*" (ring/create-resource-handler)]
      ["/notmuch-search" {:post notmuch-search}]
      ["/notmuch-show" {:get show}]
      ["/paginator" {:get paginator}]
      ])
    (constantly {:status 404, :body "Not Found."})))


(defonce server (atom nil))

(defn wrap-json-params
  "Add :json to request map when content-type is application/json"
  [handler]
  (fn [request]
    (let [body (slurp (:body request))]
      (if (and (not (= "" body)) (= "application/json" (:content-type request)))
        (handler (assoc request :json (keywordize-keys (json/read-str body))))
        (handler request)
        )
      )
    ))


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
  (selmer/cache-off!)
  (start-nrepl))



(comment
  (sanitize-query "and tag:replace ")
  (selmer/cache-off!)
)
