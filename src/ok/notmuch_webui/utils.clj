(ns ok.notmuch-webui.utils
  (:require
   [ring.util.codec :refer [form-decode]]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.edn :as edn]
   )
  (:gen-class))

(defn decode-form-params [query-string]
  (keywordize-keys (form-decode query-string))
)

(defn parse-number [n]
  (edn/read-string n)
)
