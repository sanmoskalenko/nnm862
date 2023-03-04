(ns nnm862.server.web
  (:require
   [nnm862.server.routes :refer [app-routes]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [muuntaja.middleware :as middleware] 
   [mount.core :refer [defstate]]
   [ring.adapter.jetty :refer [run-jetty]]
   [nnm862.server.config :refer [ctx]]))


(def app
  (-> app-routes
      middleware/wrap-format
      (wrap-defaults site-defaults)))


(defstate webserver
  :start (run-jetty #'app (:web ctx))
  :stop  (.stop webserver))
