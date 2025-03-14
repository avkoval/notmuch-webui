(ns ok.notmuch-webui.notmuch
  (:require
   [clojure.string :as string]
   [babashka.process :refer [shell]]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [clojure.core.cache :as cache]))

(def notmuch-binary "notmuch")
(def default-search-options {"--offset" 0, "--limit" 9, "--sort" "newest-first", "--format" "json", "--output" "summary"})
(def default-count-options {"--output" "threads"})

(defonce COUNTS (atom (cache/fifo-cache-factory {})))

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
  (let [cmd-options (merge default-count-options options)
        shell-args (concat [{:out :string :err :string :continue true} (str notmuch-binary " count") ] (conj (into [] (map #(string/join "=" %) cmd-options)) q))
        output (apply shell shell-args)
        ]
    (log/info shell-args)
    (json/read-str (:out output) {:eof-error? false :eof-value nil})
    )
)



(comment
  (search-results-count "tag:inbox" {})
)
