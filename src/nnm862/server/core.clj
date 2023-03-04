(ns nnm862.server.core
  (:require
   [mount.core :as mount]
   [nnm862.server.web :refer [webserver]]
   [nnm862.server.config :refer [ctx]]
   [cambium.core :as log])
  (:gen-class))


(defn stop! []
  (let [_      (log/info {:msg "Stop nnm862"})
        status (mount/stop #'webserver #'ctx)]
    (log/info {:status status})
    status))


(defn start! []
  (let [status (mount/start #'ctx #'webserver)
        _      (log/info {:msg "Start nnm862"})
        _      (.addShutdownHook (Runtime/getRuntime) (Thread. stop!))
        _      (log/info {:status status})]
    status))


(defn -main []
  (start!))


(comment

  (stop!)

  (start!)
  
  )