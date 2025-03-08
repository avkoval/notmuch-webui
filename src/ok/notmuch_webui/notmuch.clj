(ns ok.notmuch-webui.notmuch
  (:require
   [clojure.string :as string]
   [babashka.process :refer [shell]]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]))

(def notmuch-binary "notmuch search")
(def default-search-options {"--offset" 0, "--limit" 7, "--sort" "newest-first", "--format" "json"})

(defn search
  "Get messages list"
  [q options]
  (let [cmd-options (merge default-search-options options)
        shell-args (concat [{:out :string} notmuch-binary] (conj (into [] (map #(string/join "=" %) cmd-options)) q))]
    (log/info shell-args)
    (json/read-str (:out (apply shell shell-args)))
    )
)

(comment
)
