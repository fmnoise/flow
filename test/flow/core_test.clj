(ns flow.core-test
  (:require [clojure.test :refer :all]
            [flow.core :refer :all]))

;; some example dummy code
(defn find-entity [id]
  (if (some? id)
    {:id id :name "Jack" :role :admin}
    (fail "User not found" {:id id})))

(defn update-entity [entity data]
  (merge entity data))

(defn notify-slack [err]
  (prn "Slack notified"))

(defn format-response [data]
  {:status 200 :entity data})

(defn format-error [{:keys [cause data]}]
  {:status 500 :error cause :context data})

;; pipeline
(defn persist-changes [id data]
  (->> (call (find-entity id))
       (then #(update-entity % data))
       (then format-response)
       (thru notify-slack)
       (else (comp format-error Throwable->map))))

(deftest a-test
  (testing "ok"
    (is (= {:status 200, :entity {:id 123, :name "Jack", :role :admin, :department "IT"}}
           (persist-changes 123 {:department "IT"}))))

  (testing "fail"
    (is (= {:status 500, :error "User not found", :context {:id nil}}
           (persist-changes nil {:department "IT"})))))