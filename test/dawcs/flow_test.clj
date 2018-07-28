(ns dawcs.flow-test
  (:require [clojure.test :refer :all]
            [dawcs.flow :as f :refer :all]))

(deftest fail?--test
  (testing "with non-exception argument"
    (is (not (fail? 42))))

  (testing "with exception argument"
    (is (fail? (Exception. "oops")))))

(deftest fail--test
  (testing "with 1 argument"
    (let [e (-> (fail "oops"))
          m (Throwable->map e)]
      (is (= clojure.lang.ExceptionInfo (class e)))
      (is (= "oops" (:cause m)))
      (is (= {} (:data m)))))

  (testing "with 2 arguments"
    (testing "with 2nd map argument"
      (let [m (-> (fail "oops" {:a 1}) Throwable->map)]
        (is (= "oops" (:cause m)))
        (is (= {:a 1} (:data m)))))

    (testing "with 2nd non-map argument"
      (let [m (-> (fail "oops" 1) Throwable->map)]
        (is (= "oops" (:cause m)))
        (is (= {::f/context 1} (:data m)))))))

(deftest call--test
  (testing "without exception"
    (is (= (call #(+ 1 41)) 42)))

  (testing "with exception"
    (is (fail? (call #(throw (Exception. "oops")))))))

(deftest then--test
  (testing "with non-exception argument"
    (is (= (then inc 42) 43)))

  (testing "with exception argument"
    (let [err (fail "oops")]
      (is (= (then (constantly "ok") err) err))))

  (testing "with exception thrown inside of then handler"
    (testing "and non-exception argument given"
      (let [err (fail "oops")]
        (is (= (then (fn [_] (throw err)) 21) err))))

    (testing "and exception argument given"
      (let [err (fail "uh-oh")]
        (is (= (then (fn [_] (throw (fail "oops"))) err) err))))))

(deftest else--test
  (testing "with non-exception argument"
    (is (= (else (constantly "caught") 42) 42)))

  (testing "with exception argument"
    (is (= (else (constantly "caught")
                 (fail "oops"))
           "caught"))))

(deftest thru--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru side-fx 42)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
          side-fx #(reset! last-err %)
          res (thru side-fx err)]
      (is (= res err))
      (is (= @last-err err)))))

(deftest flet--test
  (testing "with no exception"
    (is (= (flet [x (+ 1 2)
                  y (+ x 39)]
             y)
           42)))

  (testing "with exception in bindings"
    (is (fail? (flet [x (+ 1 2)
                      y (/ x 0)]
                 y))))

  (testing "with exception in body"
    (is (fail? (flet [x (+ 1 2)
                      y 0]
                 (/ x y))))))

(deftest base-exception-class--test
  (let [f (fn [& _] (throw (Throwable. "oops")))]
    (catching Exception
      (is (thrown? Throwable (call f)))
      (is (thrown? Throwable (then f 1)))
      (is (fail? (call #(throw (Exception. "oops"))))))))

(deftest ignored-exceptions--test
  (testing "call with changed *base-exception-class*"
    (ignoring #{Exception}
      (is (thrown? IllegalArgumentException (call #(throw (IllegalArgumentException. "oops")))))
      (is (fail? (call #(throw (Throwable. "oops"))))))))
