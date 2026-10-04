(ns clj-github.token
  (:require [clojure.java.io :as io])
  (:import (java.io File IOException)))

(set! *warn-on-reflection* true)

(defn- file-exists-or-nil [^File file]
  (when (.exists file)
    file))

(defn- parse-yaml [s]
  (let [parse-string (requiring-resolve 'clj-yaml.core/parse-string)]
    (parse-string s)))

(def hub-config
  "Read token from `~/.config/hub` if the file exists.

  This credentials file is managed by https://github.com/mislav/hub.

  Users must provide their own dependency on `clj-commons/clj-yaml`."
  (memoize
   (fn []
     (some-> (io/file (System/getenv "HOME") ".config/hub")
             file-exists-or-nil
             (slurp)
             parse-yaml
             (get :github.com)
             first
             (get :oauth_token)))))

(def env-var
  "Get token from the GITHUB_TOKEN environment variable."
  (memoize
   (fn [] (System/getenv "GITHUB_TOKEN"))))

(def github-url "https://api.github.com")

(def gh-cli
  "Get token by invoking `gh auth token` if command is available.

  Requires https://github.com/cli/cli."
  (memoize
    (fn []
      (try
        (let [process (.start (ProcessBuilder. ["gh" "auth" "token"]))
              output (with-open [stdout (.getInputStream process)]
                       (String. (.readAllBytes stdout)))]
          (when (zero? (.waitFor process))
            (.trim ^String output)))
        (catch IOException _
          ; gh cli not available
          nil)))))

(defn chain [providers]
  (fn []
    (loop [providers providers]
      (when (seq providers)
        (if-let [token (-> (first providers)
                           (apply nil))]
          token
          (recur (rest providers)))))))
