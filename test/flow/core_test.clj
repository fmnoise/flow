(ns flow.core-test
  (:require [clojure.test :refer :all]
            [flow.core :refer :all]))

(deftest err?-test
  (testing "err? with non-exception argument"
    (false? (err? 42)))

  (testing "err? with exception argument"
    (true? (err? (Exception. "Oops")))))

(deftest call-test
  (testing "call without exception"
    (= (call (+ 1 41)) 42))

  (testing "call with exception should return an instance of exception"
    (isa? (call (class (throw (Exception. "Oops")))) java.lang.Throwable)))

(deftest raise-test
  (testing "raise with non-exception argument"
    (= (raise 42) 42))

  (testing "raise with exception argument should throw an exception"
    (is (thrown? java.lang.Throwable (raise (Exception. "Oops"))))))

(deftest then-test
  (testing "then with non-exception argument"
    (= (then identity 42) 42))

  (testing "then with exception argument"
    (= (then (constantly "Exception handler")
             (Exception. "Oops"))
       "Exception handler")))

(deftest then>-test
  (testing "then> with non-exception argument"
    (= (then 42 identity) 42))

  (testing "then> with exception argument"
    (= (then (Exception. "Oops")
             (constantly "Exception handler"))
       "Exception handler")))

(deftest else-test
  (testing "else with non-exception argument"
    (= (else (constantly "Exception handler") 42) 42))

  (testing "else with exception argument without class specification"
    (= (else (constantly "Exception handler")
             (Exception. "Oops"))
       "Exception handler"))

  (testing "else with exception argument and class specification equal to exception class"
    (= (else java.lang.NullPointerException
             (constantly "Exception handler")
             (java.lang.NullPointerException. "Oops"))
       "Exception handler"))

  (testing "else with exception argument and class specification non-equal to exception class"
    (isa? (class (else java.lang.NullPointerException
                       (constantly "Exception handler")
                       (java.lang.UnsupportedOperationException. "Oops")))
          java.lang.UnsupportedOperationException)))

(deftest else>-test
  (testing "else> with non-exception argument"
    (= (else> 42 (constantly "Exception handler"))))

  (testing "else> with exception argument without class specification"
    (= (else> (Exception. "Oops")
              (constantly "Exception handler"))
       "Exception handler"))

  (testing "else> with exception argument and class specification equal to exception class"
    (= (else> (java.lang.NullPointerException. "Oops")
              java.lang.NullPointerException
              (constantly "Exception handler"))
       "Exception handler"))

  (testing "else> with exception argument and class specification non-equal to exception class"
    (isa? (class (else> (java.lang.UnsupportedOperationException. "Oops")
                        java.lang.NullPointerException
                        (constantly "Exception handler")))
          java.lang.UnsupportedOperationException)))

(deftest thru-test
  (testing "thru with non-exception argument"
    (= (thru identity 42) 42))

  (testing "thru with exception argument without class specification"
    (= (thru (constantly "Exception handler")
             (Exception. "Oops"))
       "Exception handler"))

  (testing "thru with exception argument with class specification equal to exception class"
    (= (thru java.lang.NullPointerException
             (constantly "Exception handler")
             (java.lang.NullPointerException. "Oops"))
       "Exception handler"))

  (testing "thru with exception argument with class specification non- equal to exception class"
    (isa? (class (thru java.lang.NullPointerException
                       (constantly "Exception handler")
                       (java.lang.UnsupportedOperationException. "Oops")))
          java.lang.UnsupportedOperationException)))

(deftest thru>-test
  (testing "thru> with non-exception argument"
    (= (thru> 42 identity) 42))

  (testing "thru> with exception argument without class specification"
    (= (thru> (Exception. "Oops")
             (constantly "Exception handler"))
       "Exception handler"))

  (testing "thru with exception argument with class specification equal to exception class"
    (= (thru> (java.lang.NullPointerException. "Oops")
              java.lang.NullPointerException
              (constantly "Exception handler"))
       "Exception handler"))

  (testing "thru with exception argument with class specification non- equal to exception class"
    (isa? (class (thru> java.lang.NullPointerException
                        (constantly "Exception handler")
                        (java.lang.UnsupportedOperationException. "Oops")))
          java.lang.UnsupportedOperationException)))

(deftest either-test
  (testing "either with non-exception argument"
    (= (either "Exception" 42) 42))

  (testing "either with exception argument"
    (= (either "Exception" (Exception. "Oops")) "Exception")))

(deftest either>-test
  (testing "either> with non-exception argument"
    (= (either> 42 "Exception") 42))

  (testing "either> with exception argument"
    (= (either> (Exception. "Oops") "Exception") "Exception")))

(deftest flet-test
  (testing "flet with no exception"
   (= (flet [x (+ 1 2)
             y (+ x 39)]
            y)
      42))

  (testing "flet with exception"
    (isa? (class (flet [x (+ 1 2)
                        y (/ x 0)]
                       y))
          java.lang.Throwable)))
