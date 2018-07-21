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

(deftest fail!--test
  (is (thrown? clojure.lang.ExceptionInfo (fail! "oops"))))

(deftest call--test
  (testing "without exception"
    (is (= (call #(+ 1 41)) 42)))

  (testing "with exception"
    (is (fail? (call #(throw (Exception. "oops")))))))

(deftest raise--test
  (testing "with non-exception argument"
    (is (= (raise 42) 42)))

  (testing "with exception argument"
    (is (thrown? clojure.lang.ExceptionInfo (raise (fail "oops"))))))

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

(deftest then>--test
  (testing "with non-exception argument"
    (is (= (then> 42 inc) 43)))

  (testing "with exception argument"
    (let [err (Exception. "oops")]
      (is (= (then> err (constantly "ok") ) err))))

  (testing "with exception thrown inside of then handler"
    (testing "and non-exception argument given"
      (let [err (fail "oops")]
        (is (= (then> 21 (fn [_] (throw err))) err))))

    (testing "and exception argument given"
      (let [err (fail "uh-oh")]
        (is (= (then> err (fn [_] (throw (fail "oops")))) err))))))

(deftest else--test
  (testing "with non-exception argument"
    (is (= (else (constantly "caught") 42) 42)))

  (testing "with exception argument"
    (is (= (else (constantly "caught")
                      (fail "oops"))
           "caught")))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else (fn [_] (fail! "oops")) 21) 21)))

    (testing "and exception argument given"
      (let [err (fail "oops")]
        (is (= (else (fn [_] (throw err)) (fail "uh-oh")) err))))))

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
                           (NullPointerException. "oops"))
             "caught")))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")]
        (is (= (else-if NullPointerException
                             (constantly "caught")
                             err)
               err)))))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else-if NullPointerException
                           (fn [_] (fail! "oops"))
                           21)
             21)))

    (testing "and exception argument given"
      (testing "and class specification equal to exception class"
        (let [err (fail "oops")]
          (is (= (else-if NullPointerException
                               (fn [_] (throw err))
                               (NullPointerException. "oops"))
                 err))))

      (testing "and class specification non-equal to exception class"
        (let [err (UnsupportedOperationException. "oops")]
          (is (= (else-if NullPointerException
                               (fn [_] (fail "Uh-oh"))
                               err)
                 err))))))

  (testing "with wrong exeption argument"
    (is (thrown? IllegalArgumentException
                 (else-if String
                               (constantly "caught")
                               (UnsupportedOperationException. "oops"))))))

(deftest else>--test
  (testing "with non-exception argument"
    (is (= (else> 42 (constantly "caught")) 42)))

  (testing "with exception argument"
    (is (= (else> (Exception. "oops")
                       (constantly "caught"))
           "caught")))

  (testing "exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else> 21 (fn [_] (fail! "oops"))) 21)))

    (testing "and exception argument given"
      (let [err (fail "oops")]
        (is (= (else> (NullPointerException. "oops")
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
      (is (= (else-if> (NullPointerException. "oops")
                            NullPointerException
                            (constantly "caught"))
             "caught")))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")]
        (is (= (else-if> err
                              NullPointerException
                              (constantly "caught"))
               err)))))

  (testing "with exception thrown inside of handler"
    (testing "and non-exception argument given"
      (is (= (else-if> 21
                            NullPointerException
                            (fn [_] (fail! "oops")))
             21)))

    (testing "and exception argument given"
      (testing "and class specification equal to exception class"
        (let [err (fail "oops")]
          (is (= (else-if> (NullPointerException. "oops")
                                NullPointerException
                                (fn [_] (throw err)))
                 err))))

      (testing "and class specification non-equal to exception class"
        (let [err (UnsupportedOperationException. "oops")]
          (is (= (else-if> err
                                NullPointerException
                                (fn [_] (fail "Uh-oh")))
                 err))))))

  (testing "with wrong exeption class argument"
    (is (thrown? IllegalArgumentException
                 (else-if> (UnsupportedOperationException. "oops")
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
          err (Exception. "oops")
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
      (let [err (NullPointerException. "oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if NullPointerException side-fx err)]
        (is (= res err))
        (is (= @last-err err))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if NullPointerException side-fx err)]
        (is (= res err))
        (is (= @last-err nil)))))

  (testing "with wrong exception class argument"
    (is (thrown? IllegalArgumentException
                 (thru-if String
                          (constantly "caught")
                          (UnsupportedOperationException. "oops"))))))

(deftest thru>--test
  (testing "with non-exception argument"
    (let [last-err (atom nil)
          side-fx #(reset! last-err %)
          res (thru> 42 side-fx)]
      (is (= res 42))
      (is (nil? @last-err))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
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
      (let [err (NullPointerException. "oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if> err NullPointerException side-fx)]
        (is (= res err))
        (is (= @last-err err))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")
            last-err (atom nil)
            side-fx #(reset! last-err %)
            res (thru-if> err NullPointerException side-fx)]
        (is (= res err))
        (is (= @last-err nil)))))

  (testing "with wrong exception class argument"
    (is (thrown? IllegalArgumentException
                 (thru-if> (UnsupportedOperationException. "oops")
                           String
                           (constantly "caught"))))))

(deftest either--test
  (testing "with non-exception argument"
    (is (= (either "default" 42) 42)))

  (testing "with exception argument"
    (is (= (either "default" (Exception. "oops")) "default"))))

(deftest either>--test
  (testing "with non-exception argument"
    (is (= (either> 42 "default") 42)))

  (testing "with exception argument"
    (is (= (either> (Exception. "oops") "default") "default"))))

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
      (is (thrown? Throwable (else f (fail "oops"))))
      (is (fail? (call #(throw (Exception. "oops"))))))))

(deftest ignored-exceptions--test
  (testing "call with changed *base-exception-class*"
    (ignoring #{Exception}
      (is (thrown? IllegalArgumentException (call #(throw (IllegalArgumentException. "oops")))))
      (is (fail? (call #(throw (Throwable. "oops"))))))))
