(ns ow.comm.events)

(defn new-event [topic data]
  {::topic topic
   ::data data})
