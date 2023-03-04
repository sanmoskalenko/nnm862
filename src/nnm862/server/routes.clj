(ns nnm862.server.routes
  (:require
   [ring.util.http-response :as response]
   [nnm862.server.handler :refer [search-handler]]
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route]))


(defroutes app-routes
  (GET "/search" req (search-handler req))
  (route/not-found (response/not-found)))