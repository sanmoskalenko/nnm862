(ns nnm862.server.config
  (:require 
   [clojure.java.io :as io]
   [aero.core :refer [read-config]]
   [mount.core :refer [defstate]]))


(defstate ctx :start
  (read-config (io/resource "config.edn")))