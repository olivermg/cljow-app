(ns ow.app.component)

(defn get-name [{{:keys [name]} ::config :as this}]
  name)



(defn init [this name]
  (assoc this ::config {:name name}))
