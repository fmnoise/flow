(ns dawcs.flow-test
  (:require [clojure.test :refer :all]
            [dawcs.flow :refer :all]))

(deftest fail?--test
  (testing "with non-exception argument"
    (is (not (fail? 42))))

  (testing "with exception argument"
    (is (fail? (Exception. "Oops")))))

(deftest call--test
  (testing "without exception"
    (is (= (call (+ 1 41)) 42)))

  (testing "with exception"
    (is (fail? (call (throw (Exception. "Oops")))))))

(deftest raise--test
  (testing "with non-exception argument"
    (is (= (raise 42) 42)))

  (testing "with exception argument"
    (is (thrown? Exception (raise (Exception. "Oops"))))))

(deftest then--test
  (testing "with non-exception argument"
    (is (= (then inc 42) 43)))

  (testing "with exception argument"
    (let [err (Exception. "Oops")]
      (is (= (then (constantly "ok") err) err))))

  (testing "with exception thrown inside of then handler"
    (testing "and non-exception argument given"
      (let [err (fail "Oops")]
        (is (= (then (fn [_] (throw err)) 21) err))))

    (testing "and exception argument given"
      (let [err (fail "Uh-oh")]
        (is (= (then (fn [_] (throw (fail "Oops"))) err) err))))))

(deftest then>--test
  (testing "with non-exception argument"
    (is (= (then> 42 inc) 43)))

  (testing "with exception argument"
    (let [err (Exception. "Oops")]
      (is (= (then> err (constantly "ok")) err))))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (let [err (fail "Oops")]
        (is (= (then> 21 (fn [_] (throw err))) err))))

    (testing "and exception argument given"
      (let [err (fail "Uh-oh")]
        (is (= (then> err (fn [_] (throw (fail "Oops")))) err))))))

(deftest else--test
  (testing "with non-exception argument"
    (is (= (else (constantly "caught") 42) 42)))

  (testing "with exception argument"
    (is (= (else (constantly "caught")
                 (Exception. "Oops"))
           "caught")))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else (fn [_] (fail! "Oops")) 21) 21)))

    (testing "and exception argument given"
      (let [err (fail "Oops")]
        (is (= (else (fn [_] (throw err)) (fail "Uh-oh")) err))))))

(deftest else-if--test
  (testing "with non-exception argument"
    (is (= (else-if NullPointerException
                    (constantly "caught")
                    42)
           42)))

  (testing "with exception argument"
    (testing "and class specification equal to exception class"
      (is (= (else-if NullPointerException
                      (constantly "caught")
                      (NullPointerException. "Oops"))
             "caught")))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "Oops")]
        (is (= (else-if NullPointerException
                        (constantly "caught")
                        err)
               err)))))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else-if NullPointerException
                      (fn [_] (fail! "Oops"))
                      21)
             21)))

    (testing "and exception argument given"
      (testing "and class specification equal to exception class"
        (let [err (fail "Oops")]
          (is (= (else-if NullPointerException
                          (fn [_] (throw err))
                          (NullPointerException. "Oops"))
                 err))))

      (testing "and class specification non-equal to exception class"
        (let [err (UnsupportedOperationException. "Oops")]
          (is (= (else-if NullPointerException
                          (fn [_] (fail "Uh-oh"))
                          err)
                 err))))))

  (testing "with wrong exeption argument"
    (is (thrown? IllegalArgumentException
                 (else String
                       (constantly "caught")
                       (UnsupportedOperationException. "Oops"))))))

(deftest else>--test
  (testing "with non-exception argument"
    (is (= (else> 42 (constantly "caught")) 42)))

  (testing "with exception argument"
    (is (= (else> (Exception. "Oops")
                  (constantly "caught"))
           "caught")))

  (testing "exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else> 21 (fn [_] (fail! "Oops"))) 21)))

    (testing "and exception argument given"
      (let [err (fail "Oops")]
        (is (= (else> (NullPointerException. "Oops")
                     (fn [_] (throw err)))
               err))))))

(deftest else-if>--test
  (testing "with non-exception argument"
    (is (= (else-if> 42
                     NullPointerException
                     (constantly "caught"))
           42)))

  (testing "with exception agrument"
    (testing "and class specification equal to exception class"
      (is (= (else-if> (NullPointerException. "Oops")
                       NullPointerException
                       (constantly "caught"))
             "caught")))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "Oops")]
        (is (= (else-if> err
                         NullPointerException
                         (constantly "caught"))
               err)))))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else-if> 21
                       NullPointerException
                       (fn [_] (fail! "Oops")))
             21)))

    (testing "and exception argument given"
      (testing "and class specification equal to exception class"
        (let [err (fail "Oops")]
          (is (= (else-if> (NullPointerException. "Oops")
                           NullPointerException
                           (fn [_] (throw err)))
                 err))))

      (testing "and class specification non-equal to exception class"
        (let [err (UnsupportedOperationException. "Oops")]
          (is (= (else-if> err
                           NullPointerException
                           (fn [_] (fail "Uh-oh")))
                 err))))))

  (testing "with wrong exeption class argument"
    (is (thrown? IllegalArgumentException
                 (else-if> (UnsupportedOperationException. "Oops")
                           String
                           (constantly "caught"))))))

(deftest thru--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru side-fx 42)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "Oops")
          side-fx #(reset! last-err %)
          res (thru side-fx err)]
      (is (= res err))
      (is (= @last-err err)))))

(deftest thru-if--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru-if NullPointerException side-fx 42)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (testing "and class specification equal to exception class"
      (let [err (NullPointerException. "Oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if NullPointerException side-fx err)]
        (is (= res err))
        (is (= @last-err err))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "Oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if NullPointerException side-fx err)]
        (is (= res err))
        (is (= @last-err nil)))))

  (testing "with wrong exception class argument"
    (is (thrown? IllegalArgumentException
                 (thru-if String
                          (constantly "caught")
                          (UnsupportedOperationException. "Oops"))))))

(deftest thru>--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru> 42 side-fx)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "Oops")
          side-fx #(reset! last-err %)
          res (thru> err side-fx)]
      (is (= res err))
      (is (= @last-err err)))))

(deftest thru-if>--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru-if> 42 NullPointerException side-fx)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (testing "and class specification equal to exception class"
      (let [err (NullPointerException. "Oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if> err NullPointerException side-fx)]
        (is (= res err))
        (is (= @last-err err))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "Oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if> err NullPointerException side-fx)]
        (is (= res err))
        (is (= @last-err nil)))))

  (testing "with wrong exception class argument"
    (is (thrown? IllegalArgumentException
                 (thru-if> (UnsupportedOperationException. "Oops")
                           String
                           (constantly "caught"))))))

(deftest either--test
  (testing "with non-exception argument"
    (is (= (either "default" 42) 42)))

  (testing "with exception argument"
    (is (= (either "default" (Exception. "Oops")) "default"))))

(deftest either>--test
  (testing "with non-exception argument"
    (is (= (either> 42 "default") 42)))

  (testing "with exception argument"
    (is (= (either> (Exception. "Oops") "default") "default"))))

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
