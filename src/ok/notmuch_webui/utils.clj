(ns ok.notmuch-webui.utils
  (:require
   [ring.util.codec :refer [form-decode]]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   )
  (:gen-class))

(defn pprint [obj]
  (pp/pprint obj))

(defn decode-form-params [query-string]
  (if (nil? query-string) {} (keywordize-keys (form-decode query-string))))

(defn isdigits? [s] (every? #(Character/isDigit %) s))

(defn parse-number [n]
  (edn/read-string n))

(defn parse-number-alt
  ([s] (parse-number-alt s nil))
  ([s default]
   (if (and (not (nil? s)) (isdigits? s))
     (Integer/parseInt s)
     default
     )))
