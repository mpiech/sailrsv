(ns sailrsv.core
  (:require 
   [nrepl.server :as nrepl]
   [clj-time.core :as time]
   [clj-time.format :as ftime]
   [clj-time.coerce :as ctime]
   [clojure.java.jdbc :as jdbc]
   [net.cgrand.enlive-html :as enlive]
   )
  (:import [java.net URL]
           [java.io DataOutputStream])
  (:gen-class)
  )

;;;
;;; Static parameters
;;;

(def loc-timezone "America/Los_Angeles")
(def lookahead-days 90)

;;;
;;; SQL database of reservations
;;;

(def rsvdb (System/getenv "RSVDB"))
(def rsvdbtype (System/getenv "RSVDBTYPE"))

(def dbspec
  (case rsvdb
    "openshift" {:connection-uri
                 (str 
                  "jdbc:mysql://"
                  (System/getenv "MYSQL_SERVICE_HOST") ":"
                  (System/getenv "MYSQL_SERVICE_PORT") "/"
                  (System/getenv "SLCAL_SQLDB")
                  "?user=" (System/getenv "SLCAL_SQLUSR")
                  "&password=" (System/getenv "SLCAL_SQLPWD")
                  "&useSSL=false"
                  )
                 }
    "crunchy" {:dbtype rsvdbtype
               :dbname (System/getenv "SLCAL_SQLDB")
               :host (System/getenv "PGHOST")
               :user (System/getenv "PGUSER")
               :password (System/getenv "PGPASSWORD")
               :ssl true
               :sslmode "require"
               }
    "cockroach" {:connection-uri
                 (str 
                  "jdbc:postgresql://"
                  (System/getenv "COCKROACH_HOST") ":"
                  (System/getenv "COCKROACH_PORT") "/"
                  (System/getenv "SLCAL_SQLDB")
                  "?user=" (System/getenv "COCKROACH_USR")
                  "&password=" (System/getenv "COCKROACH_PWD")
                  "&options=" (System/getenv "COCKROACH_OPTIONS")
                  )
                 }
    "local" {:dbtype rsvdbtype
             :dbname (System/getenv "SLCAL_SQLDB")
             :subname (str
                       "//localhost:3306/"
                       (System/getenv "SLCAL_SQLDB"))
             :user (System/getenv "SLCAL_SQLUSR")
             :password (System/getenv "SLCAL_SQLPWD")
             }
    ))

;;; parameters related to reservation server

(def rsv-url (System/getenv "RSV_URL"))

(def rsv-static-params (System/getenv "RSV_STATIC_PARAMS"))

(def scrape-string (System/getenv "RSV_SCRAPE_STRING"))


;;; build the arg list for the HTTP POST to reservation server

(defn build-rsv-params [cur-yr cur-mo cur-dy
                        fr-yr fr-mo fr-dy fr-tm
                        to-yr to-mo to-dy]
  (str
   rsv-static-params
   "&todaydate="
   cur-mo "%20"
   cur-dy "%20"
   cur-yr
   "&fromdate="
   fr-mo "%20"
   fr-dy "%20"
   fr-yr
   "&fromtime="
   fr-tm
   "&todate="
   to-mo "%20"
   to-dy "%20"
   to-yr
   "&totime=08:00")
  )

;;;
;;; Screen scraping utility (post, return Enlive rsc)
;;;

(defn http-scrape [url params]   
  (let [connection (.openConnection (URL. url))]   
    (doto connection   
      (.setRequestMethod "POST")   
      (.setDoOutput true)   
      )
    (let [outstrm (.getOutputStream connection)]
      (doto (DataOutputStream. outstrm)
        (.writeBytes params)
        (.flush)
        (.close)))
    (enlive/html-resource (.getInputStream connection))))

(defn http-json [url params]   
  (let [connection (.openConnection (URL. url))]   
    (doto connection   
      (.setRequestMethod "POST")   
      (.setDoOutput true)   
      )
    (let [outstrm (.getOutputStream connection)]
      (doto (DataOutputStream. outstrm)
        (.writeBytes params)
        (.flush)
        (.close)))
    (slurp (.getInputStream connection))))

;;;
;;; Scrape functions
;;;

;;; to determine if boat is reserved on a specific date
;;; post to reservations URL with constructed param string and
;;; then look for scrape-string in HTML snippet parsed
;;; out using Enlive

(defn rsv-scrape-reserved? [rsv-params]
  (neg? (.indexOf
         (map enlive/text
              (enlive/select
               (http-scrape rsv-url rsv-params)
               [:td.yctablecell]))
         scrape-string)))

;;;
;;; Date/Time utilities
;;;

(defn get-cur-dtobj []
  (time/to-time-zone
   (time/now)
   (time/time-zone-for-id loc-timezone)))

(defn sqldtobj-to-dtobj [sqldtobj]
  (time/to-time-zone
   (ctime/from-sql-time sqldtobj)
   (time/time-zone-for-id loc-timezone)))

; unused
(defn sqldtobj-to-datestr [sqldtobj]
  (ftime/unparse
   (ftime/with-zone
     (ftime/formatters :date)
     (time/time-zone-for-id loc-timezone))
   (ctime/from-sql-time sqldtobj)))

(defn dtobj-to-dtstr [dtobj]
  (ftime/unparse
   (ftime/with-zone
     (ftime/formatters :date-hour-minute-second)
     (time/time-zone-for-id loc-timezone))
   dtobj))
  
(defn dtobj-to-datestr [dtobj]
  (ftime/unparse
   (ftime/with-zone
     (ftime/formatters :date)
     (time/time-zone-for-id loc-timezone))
   dtobj))

(defn dtobjs-to-datestrs [dtobjs]
  (map (fn [x]
         (dtobj-to-datestr x))
       dtobjs))

; debugging test function

(defn scrape-day [daysout]
  (let [format-mo (ftime/formatter-local "MMM")
        cur-dtobj (get-cur-dtobj)
        cur-yr (time/year cur-dtobj)
        cur-mo (ftime/unparse format-mo cur-dtobj)
        cur-dy (time/day cur-dtobj)
        check-dtstr (dtobj-to-dtstr cur-dtobj)
        fr-dtobj (time/plus cur-dtobj
                            (time/days daysout))
        fr-yr (time/year fr-dtobj)
        fr-mo (ftime/unparse format-mo fr-dtobj)
        fr-dy (time/day fr-dtobj)
        fr-tm (if (= daysout 0) "14:00" "09:00")
        fr-dtstr (dtobj-to-dtstr fr-dtobj)
        to-dtobj (time/plus fr-dtobj (time/days 1))
        to-yr (time/year to-dtobj)
        to-mo (ftime/unparse format-mo to-dtobj)
        to-dy (time/day to-dtobj)
        rsv-params (build-rsv-params
                    cur-yr cur-mo cur-dy
                    fr-yr fr-mo fr-dy fr-tm
                    to-yr to-mo to-dy)
        ]
    (http-scrape rsv-url rsv-params)
    ))

;;;
;;; Database functions
;;;

;;; database has table "reservations" with
;;; columns "check_date" and "date" where, for every
;;; date for which the boat is reserved, there is a row
;;; containing the date on which the check occurred and
;;; the date reserved and "cancellations" table
;;; with check_date (i.e. orig rsv date), res_date,
;;; and cancel_date

(defn db-add-rsv [check-dtobj res-dtobj]
  (let [check-dtstr 
        (ftime/unparse
         (ftime/with-zone
           (ftime/formatters :date-hour-minute-second)
           (time/default-time-zone))
         check-dtobj)
        res-dtstr
        (ftime/unparse
         (ftime/with-zone
           (ftime/formatters :date-hour-minute-second)
           (time/default-time-zone))
         res-dtobj)]
    (println "")
    (println "Adding " check-dtstr " " res-dtstr)
    (jdbc/insert! dbspec :reservations
                  {:check_date check-dtstr
                   :res_date res-dtstr})))

(defn db-cancel-rsv [check-dtobj res-dtobj]
  (let [check-dtstr 
        (ftime/unparse
         (ftime/with-zone
           (ftime/formatters :date-hour-minute-second)
           (time/default-time-zone))
         check-dtobj)
        res-datestr
        (ftime/unparse
         (ftime/with-zone
           (ftime/formatters :date)
           (time/default-time-zone))
         res-dtobj)]
    (println "Canceling " res-datestr)
    (if (= rsvdbtype "mysql")
      (jdbc/delete! dbspec :reservations
                    ["DATE(res_date)=?" res-datestr])
      (jdbc/delete! dbspec :reservations
                    [(str "res_date=? CAST (" res-datestr
                          " AS TIMESTAMP")]))
    (jdbc/insert! dbspec :cancellations
                  {:cancel_date check-dtstr
                   :res_date res-datestr})))

(defn db-read-dtobjs [table start-dtstr]
  (let [qstr (if (= rsvdbtype "mysql")
               (str
                "SELECT DISTINCT res_date "
                "FROM " table
                " WHERE res_date >= \""
                start-dtstr
                "\"")
               (str
                "SELECT DISTINCT res_date "
                "FROM " table
                " WHERE CAST (res_date AS TIMESTAMP) >= "
                "CAST ('" start-dtstr "' AS TIMESTAMP)"))]
    (map (fn [x]
           (sqldtobj-to-dtobj (:res_date x)))
         (jdbc/query dbspec [qstr]))
    ))

(defn reserved? [test-dtobj prev-rsvs-dtobjs]
  (let [test-datestr (dtobj-to-datestr test-dtobj)]
    (some #(= test-datestr (dtobj-to-datestr %))
          prev-rsvs-dtobjs)))

;;; loops over a range of dates, scraping reserved
;;; status from reservation server and writes to database

(defn db-write-rsvs [start numdays]
  (let [format-mo (ftime/formatter-local "MMM")
        cur-dtobj (get-cur-dtobj)
        cur-yr (time/year cur-dtobj)
        cur-mo (ftime/unparse format-mo cur-dtobj)
        cur-dy (time/day cur-dtobj)
        check-dtstr (dtobj-to-dtstr cur-dtobj)
        prev-rsvs-dtobjs (db-read-dtobjs 
                          "reservations"
                          check-dtstr)]
    (doseq [offset (range start (+ start numdays))]
      (let [fr-dtobj (time/plus cur-dtobj
                                (time/days offset))
            fr-yr (time/year fr-dtobj)
            fr-mo (ftime/unparse format-mo fr-dtobj)
            fr-dy (time/day fr-dtobj)
            fr-tm (if (= offset 0) "14:00" "09:00")
            fr-dtstr (dtobj-to-dtstr fr-dtobj)
            to-dtobj (time/plus fr-dtobj (time/days 1))
            to-yr (time/year to-dtobj)
            to-mo (ftime/unparse format-mo to-dtobj)
            to-dy (time/day to-dtobj)
            rsv-params (build-rsv-params
                       cur-yr cur-mo cur-dy
                       fr-yr fr-mo fr-dy fr-tm
                       to-yr to-mo to-dy)
            now-rsvd (rsv-scrape-reserved? rsv-params)
            was-rsvd (reserved? fr-dtobj prev-rsvs-dtobjs)]
        (if now-rsvd
          (if-not was-rsvd
            (db-add-rsv cur-dtobj fr-dtobj))
          (if was-rsvd
            (db-cancel-rsv cur-dtobj fr-dtobj)))))))

;;;
;;; debugging examples for REPL
;;;

(comment
  (jdbc/query dbspec
              ["select * from reservations"])
  (jdbc/insert! dbspec :bareboat
                {:res_date "2016-04-08"})
  )

(defn -main
  "Sailboat reservation screenscraper & reservation writer"
  [& args]
  ; uncomment nrepl line below to debug with nrepl
  ;(defonce server (nrepl/start-server :port 7888))
  (let [start 1
        numdays lookahead-days]
    (db-write-rsvs start numdays)
    ; uncomment infinite loop below to jack in with nREPL and debug
    ;(while true nil)
    ))

;;; EOF
