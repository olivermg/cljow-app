(ns ow.app.msgcomponent-test
  #_(:require [clojure.core.async :as a]
            [clojure.test :refer :all :as t]
            [ow.app.msgcomponent :as sut]
            [ow.app.lifecycle :as owl]))

(comment
  (defonce ^:private invocation-history (atom []))

  (defrecord FooComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

  (defn foo-component [in-mult out-chan]
    (let [dm {:foo1 (fn [this data]
                      (swap! invocation-history #(conj % :foo/foo1))
                      (update data :x inc))
              :foo2 (fn [this data]
                      (swap! invocation-history #(conj % :foo/foo2))
                      (->> (update data :x inc)
                           (sut/emit this :bar2)
                           (sut/wait this)))}]
      (-> (->FooComponent)
          (sut/msgify in-mult out-chan dm))))

  (defrecord BarComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

  (defn bar-component [in-mult out-chan]
    (let [dm {:bar1 (fn [this data]
                      (swap! invocation-history #(conj % :bar/bar1))
                      (update data :x #(* % 2)))
              :bar2 (fn [this data]
                      (swap! invocation-history #(conj % :bar/bar2))
                      (->> (update data :x #(* % 2))
                           (sut/emit this :baz1)
                           (sut/wait this)))}]
      (-> (->BarComponent)
          (sut/msgify in-mult out-chan dm))))

  (defrecord BazComponent []
    owl/Lifecycle
    (start* [this parent]
      this)
    (stop* [this parent]
      this))

  (defn baz-component [in-mult out-chan]
    (let [dm {:baz1 (fn [this data]
                      (swap! invocation-history #(conj % :baz/baz1))
                      (update data :x #(/ % 3)))}]
      (-> (->BazComponent)
          (sut/msgify in-mult out-chan dm))))


  (defn- init []
    (let [in-chan  (a/chan)
          in-mult  (a/mult in-chan)
          out-chan (a/chan)]
      (a/pipe out-chan in-chan)
      (reset! invocation-history [])
      [in-chan in-mult out-chan]))


  (deftest single-async
    (let [[in-chan in-mult out-chan] (init)
          foo (-> (foo-component in-mult out-chan)
                  owl/start)]
      (sut/emit foo :foo1 {:x 100})
      (Thread/sleep 50)
      (is (= [:foo/foo1] @invocation-history))
      (owl/stop foo)))

  (deftest single-sync
    (let [[in-chan in-mult out-chan] (init)
          foo (-> (foo-component in-mult out-chan)
                  owl/start)
          result (->> (sut/emit foo :foo1 {:x 100})
                      (sut/wait foo))]
      (is (= [:foo/foo1] @invocation-history))
      (is (= {:x 101} result))
      (owl/stop foo)))

  (deftest multi-serial-sync
    (let [[in-chan in-mult out-chan] (init)
          foo (-> (foo-component in-mult out-chan)
                  owl/start)
          bar (-> (bar-component in-mult out-chan)
                  owl/start)
          baz (-> (baz-component in-mult out-chan)
                  owl/start)
          result (->> (sut/emit foo :foo2 {:x 100})
                      (sut/wait foo))]
      (is (= [:foo/foo2 :bar/bar2 :baz/baz1] @invocation-history))
      (is (= {:x (-> 101 (* 2) (/ 3))} result))
      (owl/stop baz)
      (owl/stop bar)
      (owl/stop foo))))
