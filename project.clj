(defproject fmnoise/flow "4.0.0"
  :description "Functional style of errors handling (without monads)"
  :url "https://github.com/fmnoise/flow"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :plugins [[lein-doo "0.1.11"]]
  :java-source-paths ["src/fmnoise/flow"]
  :javac-options ["-target" "1.7", "-source" "1.7"])
