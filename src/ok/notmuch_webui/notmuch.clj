(ns ok.notmuch-webui.notmuch
  (:require
   [clojure.string :as string]
   [babashka.process :refer [shell]]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [clojure.core.cache :as cache]
   [ok.notmuch-webui.utils :as utils]))

(def notmuch-binary "notmuch")
(def default-search-options {"--offset" 0, "--limit" 10, "--sort" "newest-first", "--format" "json", "--output" "summary"})
(def default-count-options {"--output" "threads"})
(def default-show-options {"--format" "json"})

(def C2 (atom (cache/fifo-cache-factory {})))

(defn search
  "Get messages list"
  [q options]
  (let [cmd-options (merge default-search-options options)
        shell-args (concat [{:out :string :err :string :continue true} (str notmuch-binary " search") ] (conj (into [] (map #(string/join "=" %) cmd-options)) q))
        output (apply shell shell-args)
        errors (:err output nil)
        ]
    (log/info shell-args)
    {:messages (json/read-str (:out output) {:eof-error? false :eof-value nil}) :err errors}
    )
)

(defn search-results-count
  "Count messages list"
  [q options]
  (println "ok-2025-03-15-1742020889")
  (let [cmd-options (merge default-count-options options)
        shell-args (concat [{:out :string :err :string :continue true} (str notmuch-binary " count") ] (conj (into [] (map #(string/join "=" %) cmd-options)) q))
        output (apply shell shell-args)
        ]
    (log/info shell-args)
    (json/read-str (:out output) {:eof-error? false :eof-value nil})
    )
)

(defn search-results-count-cached 
  "Cache results. 
  TODO: cache invalidation on db change
  TODO: cache TTL"
  [q options]
  (let [cache-key (str (hash q))
        cache (if (cache/has? @C2 cache-key)
                (cache/hit @C2 cache-key)
                (cache/miss @C2 cache-key (search-results-count q options)))
        value (get cache cache-key)
        ]
    (swap! C2 cache/through-cache cache-key (constantly value))
    value
    ))

(defn make-shell-args 
  "Make shell arguments array from map, but if argument value is empty, then don't add it via ="
  [cmd-options]
  (into [] (map (fn [args] (if (= "" (last args)) (first args) (string/join "=" args))) cmd-options)))


(defn show
  "Show messages"
  [q options]
  (println "ok-2025-03-15-1742025822" q)
  (let [cmd-options (merge default-show-options options)
        shell-args (concat [{:out :string :err :string :continue true} (str notmuch-binary " show") ] (conj (make-shell-args cmd-options) q))
        output (apply shell shell-args)
        ]
    (log/info shell-args)
    (json/read-str (:out output) {:eof-error? false :eof-value nil})
    )
  )

(defn show-part [message-id part-id]
  (let [cmd-options (merge default-show-options {"--part" (utils/parse-number-alt part-id) "--include-html" ""})
        show-args (str "id:" message-id)
        message (show show-args cmd-options)
        ]
    (println cmd-options)
    message
    )  
)

(comment
  (search-results-count "tag:inbox" {})
  (swap! C2 cache/through-cache :a (constantly 12))
)
