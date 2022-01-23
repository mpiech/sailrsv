(defproject sailrsv "0.4.0"
  :description "Sailboat reservation screenscraper & reservation writer"
  :url "http://piech.io"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies 
  [;http://clojure.org/downloads
   [org.clojure/clojure "1.10.0"]
   ;https://github.com/nrepl/nrepl
   [nrepl "0.9.0"]
   ;https://github.com/clj-time/clj-time
   [clj-time "0.15.2"]
   ;https://github.com/cgrand/enlive
   [enlive "1.1.6"]
   ;https://github.com/clojure/java.jdbc
   [org.clojure/java.jdbc "0.7.12"]
   ;http://dev.mysql.com/downloads/connector/j/
   [mysql/mysql-connector-java "8.0.28"]
   ]
  :main sailrsv.core
  :aot [sailrsv.core]
  :profiles {:uberjar {:aot :all}}
  )
