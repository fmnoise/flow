(ns flow.core-test
  (:require [clojure.test :refer :all]
            [flow.core :refer :all]))

(deftest fail?--test
  (testing "fail? with non-exception argument"
    (is (not (fail? 42))))

  (testing "fail? with exception argument"
    (is (fail? (Exception. "Oops")))))

(deftest call--test
  (testing "call without exception"
    (is (= (call (+ 1 41)) 42)))

  (testing "call with exception should return an instance of exception"
    (is (fail? (call (throw (Exception. "Oops")))))))

(deftest raise--test
  (testing "raise with non-exception argument"
    (is (= (raise 42) 42)))

  (testing "raise with exception argument should throw an exception"
    (is (thrown? Exception (raise (Exception. "Oops"))))))

(deftest then--test
  (testing "then with non-exception argument"
    (is (= (then inc 42) 43)))

  (testing "then with exception argument"
    (let [err (Exception. "Oops")]
      (is (= (then (constantly "ok") err) err)))))

(deftest then>--test
  (testing "then> with non-exception argument"
    (is (= (then> 42 inc) 43)))

  (testing "then> with exception argument"
    (let [err (Exception. "Oops")]
      (is (= (then> err (constantly "ok")) err)))))

(deftest else--test
  (testing "else with non-exception argument"
    (is (= (else (constantly "caught") 42) 42)))

  (testing "else with exception argument without class specification"
    (is (= (else (constantly "caught")
                 (Exception. "Oops"))
           "caught")))

  (testing "else with exception argument and class specification equal to exception class"
    (is (= (else NullPointerException
                 (constantly "caught")
                 (NullPointerException. "Oops"))
           "caught")))

  (testing "else with exception argument and class specification non-equal to exception class"
    (let [err (UnsupportedOperationException. "Oops")]
      (is (= err (else NullPointerException
                       (constantly "caught")
                       err)))))

  (testing "else with exeption argument of non-exeption class"
    (is (thrown? IllegalArgumentException
                 (else String
                       (constantly "caught")
                       (UnsupportedOperationException. "Oops"))))))

(deftest else>--test
  (testing "else> with non-exception argument"
    (is (= (else> 42 (constantly "caught")))))

  (testing "else> with exception argument without class specification"
    (is (= (else> (Exception. "Oops")
                  (constantly "caught"))
           "caught")))

  (testing "else> with exception argument and class specification equal to exception class"
    (is (= (else> (NullPointerException. "Oops")
                  NullPointerException
                  (constantly "caught"))
           "caught")))

  (testing "else> with exception argument and class specification non-equal to exception class"
    (let [err (UnsupportedOperationException. "Oops")]
      (is (= (else> err NullPointerException (constantly "caught")))
          err))))

(deftest thru--test
  (testing "thru with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru side-fx 42)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "thru with exception argument without class specification"
    (let [last-err (atom nil)
          err (Exception. "Oops")
          side-fx #(reset! last-err %)
          res (thru side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "thru with exception argument with class specification equal to exception class"
    (let [err (NullPointerException. "Oops")
          last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru NullPointerException side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "thru with exception argument with class specification non-equal to exception class"
    (let [err (UnsupportedOperationException. "Oops")
          last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru NullPointerException side-fx err)]
      (is (= res err))
      (is (= @last-err nil))))

  (testing "thru with exception argument of non-exception class"
    (is (thrown? IllegalArgumentException
                 (thru String
                       (constantly "caught")
                       (UnsupportedOperationException. "Oops"))))))

(deftest thru>--test
  (testing "thru> with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru> 42 side-fx)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "thru> with exception argument without class specification"
    (let [last-err (atom nil)
          err (Exception. "Oops")
          side-fx #(reset! last-err %)
          res (thru> err side-fx)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "thru> with exception argument with class specification equal to exception class"
    (let [err (NullPointerException. "Oops")
          last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru> err NullPointerException side-fx)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "thru> with exception argument with class specification non-equal to exception class"
    (let [err (UnsupportedOperationException. "Oops")
          last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru> err NullPointerException side-fx)]
      (is (= res err))
      (is (= @last-err nil)))))

(deftest either--test
  (testing "either with non-exception argument"
    (is (= (either "default" 42) 42)))

  (testing "either with exception argument"
    (is (= (either "default" (Exception. "Oops")) "default"))))

(deftest either>--test
  (testing "either> with non-exception argument"
    (is (= (either> 42 "default") 42)))

  (testing "either> with exception argument"
    (is (= (either> (Exception. "Oops") "default") "default"))))

(deftest flet--test
  (testing "flet with no exception"
    (is (= (flet [x (+ 1 2)
                  y (+ x 39)]
                 y)
           42)))

  (testing "flet with exception in bindings"
    (is (fail? (flet [x (+ 1 2)
                     y (/ x 0)]
                    y))))

  (testing "flet with exception in body"
    (is (fail? (flet [x (+ 1 2)
                     y 0]
                    (/ x y))))))

(deftest base-exception-class--test
  (testing "call with changed *base-exception-class*"
    (catching Exception
      (is (thrown? Throwable (call (throw (Throwable. "Oops")))))))

  (testing "then with changed *base-exception-class*"
    (catching Exception
      (is (thrown? Throwable (then (constantly (throw (Throwable. "Oops")))
                                   1)))))
  (testing "else with changed *base-exception-class*"
    (catching Exception
      (is (thrown? Throwable (else (constantly (throw (Throwable. "Oops")))
                                   (fail "Oops")))))))
