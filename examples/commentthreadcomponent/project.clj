(defproject commentthreadcomponent "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [grape "0.1.0-SNAPSHOT"]
                 [com.novemberain/monger "3.0.2"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [ring/ring-json "0.4.0"]
                 [prismatic/schema "1.1.1"]
                 [bidi "2.0.9"]]
  :main ^:skip-aot commentthreadcomponent.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
