(ns fmnoise.flow-test
  (:require [clojure.test :refer [deftest testing is]]
            [fmnoise.flow :as f]))

(defrecord Left [error]
  f/Flow
  (?ok [this _] this)
  (?err [this f] (f (ex-info "Either.Left" this)))
  (?throw [this] (throw (ex-info "Either.Left" this))))

(defrecord Right [value]
  f/Flow
  (?ok [this f] (f (:value this)))
  (?err [this _] this)
  (?throw [this] this))

(deftest fail?--test
  (testing "with non-exception argument"
    (is (not (f/fail? 42))))

  (testing "with exception argument"
    (is (f/fail? (Exception. "oops"))))

  (testing "with custom error class"
    (is (f/fail? (Left. "uh-oh"))))

  (testing "with custom value class"
    (is (not (f/fail? (Right. 1))))))

(deftest fail-with--test
  (testing "with empty map"
    (let [e (f/fail-with {})]
      (is (= fmnoise.flow.Fail (class e)))
      (is (instance? clojure.lang.IExceptionInfo e) "implements IExceptionInfo")
      (is (instance? RuntimeException e) "extends RuntimeException")
      (is (empty? (.getStackTrace (f/fail-with {}))) "stacktrace is disabled by default")
      (is (nil? (.getMessage e)))
      (is (= {} (ex-data e)))))

  (testing "with non-empty map"
    (is (= "oops" (.getMessage (f/fail-with {:msg "oops"}))))
    (is (= {:id 1} (ex-data (f/fail-with {:data {:id 1}}))))
    (let [err (Exception. "uh-oh")]
      (is (= err (.getCause (f/fail-with {:cause err})))))
    (testing "with enabled stacktrace"
      (is (not (empty? (.getStackTrace (f/fail-with {:trace? true}))))))))

(deftest fail-with!--test
  (is (thrown? fmnoise.flow.Fail (f/fail-with! {})))
  (testing "stacktrace is enabled by default"
    (is (not (empty? (.getStackTrace (try (f/fail-with! {}) (catch Throwable e e)))))))
  (testing "with stacktrace disabled"
    (is (empty? (.getStackTrace (try (f/fail-with! {:trace? false}) (catch Throwable e e)))))))

(deftest call--test
  (testing "without exception"
    (is (= 42 (f/call #(+ 1 41)))))

  (testing "with exception"
    (is (f/fail? (f/call #(throw (Exception. "oops")))))))

(deftest call-with--test
  (testing "without exception thrown inside function"
    (is (= 42 (f/call-with identity #(+ 1 41)))))

  (testing "with exception thrown inside function"
    (testing "and non-throwing handler"
      (is (f/fail? (f/call-with identity #(throw (Exception. "oops"))))))

    (testing "and throwing handler"
      (is (thrown? Exception (f/call-with #(throw %) #(throw (Exception. "oops"))))))))

(deftest chain--test
  (is (= 5 (f/chain 1 inc (partial * 3) dec)))
  (let [err (ex-info "oops" {})]
    (is (= err (f/chain 1 inc (partial * 3) (constantly err) dec)))))

(deftest then--test
  (testing "with non-fail argument"
    (is (= 43 (f/then inc 42))))

  (testing "with fail argument"
    (let [err (Exception. "oops")]
      (is (= err (f/then (constantly "ok") err))))

    (testing "using custom fail class"
      (is (= 2 (f/then inc (Right. 1))))
      (is (= (Left. "uh-oh") (f/then (constantly "ok") (Left. "uh-oh"))))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (thrown? Exception (f/then (fn [_] (throw err)) 21)))))

    (testing "and fail argument"
      (let [err (Exception. "uh-oh")]
        (is (= err (f/then (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest then-call--test
  (testing "with non-exception argument"
    (is (= 43 (f/then-call inc 42))))

  (testing "with exception argument"
    (let [err (Exception. "oops")]
      (is (= err (f/then-call (constantly "ok") err))))

    (testing "using custom fail class"
      (is (= 2 (f/then-call inc (Right. 1))))
      (is (= (Left. "uh-oh") (f/then (constantly "ok") (Left. "uh-oh"))))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= err (f/then-call (fn [_] (throw err)) 21)))))

    (testing "and fail argument"
      (let [err (Exception. "uh-oh")]
        (is (= err (f/then-call (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest else--test
  (testing "with non-fail argument"
    (is (= 42 (f/else (constantly "caught") 42))))

  (testing "with fail argument"
    (is (= "caught" (f/else (constantly "caught") (Exception. "oops"))))

    (testing "using custom fail class"
      (is (= (Right. 1) (f/else (constantly "caught") (Right. 1))))
      (is (= "caught" (f/else (constantly "caught") (Left. "uh-oh"))))))

  (testing "with exception thrown inside of function"
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= 21 (f/else (fn [_] (throw err)) 21)))))

    (testing "and fail argument"
      (let [err (RuntimeException. "uh-oh")]
        (is (thrown? Exception (f/else (fn [_] (throw (Exception. "oops"))) err)))))))

(deftest else-call--test
  (testing "with non-exception argument"
    (is (= 42 (f/else-call (constantly "caught") 42))))

  (testing "with fail argument"
    (is (= "caught" (f/else-call (constantly "caught") (Exception. "oops"))))

    (testing "using custom fail class"
      (is (= (Right. 1) (f/else-call (constantly "caught") (Right. 1))))
      (is (= "caught" (f/else-call (constantly "caught") (Left. "uh-oh"))))))

  (testing "with exception thrown inside of function "
    (testing "and non-exception argument"
      (let [err (Exception. "oops")]
        (is (= 21 (f/else-call (fn [_] (throw err)) 21)))))

    (testing "and exception argument"
      (let [err (RuntimeException. "uh-oh")]
        (is (= err (f/else-call (fn [_] (throw err)) (Exception. "oops"))))))))

(deftest thru--test
  (testing "with non-exception argument"
    (let [state (atom nil)
          side-fx #(reset! state (inc %))
          res (f/thru side-fx 42)]
      (is (= 42 res))
      (is (= 43 @state))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
          side-fx #(reset! last-err %)
          res (f/thru side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "with exception thrown inside of function"
    (is (thrown? Exception (f/thru #(throw %) (Exception. "oops"))))))

(deftest thru-call--test
  (testing "with non-exception argument"
    (let [state (atom nil)
          side-fx #(reset! state (inc %))
          res (f/thru-call side-fx 42)]
      (is (= 42 res))
      (is (= 43 @state))))

  (testing "with exception argument"
    (let [last-err (atom nil)
          err (Exception. "oops")
          side-fx #(reset! last-err %)
          res (f/thru-call side-fx err)]
      (is (= res err))
      (is (= @last-err err))))

  (testing "with exception thrown inside of function"
    (is (= 21 (f/thru-call (fn [_] (throw (Exception. "oops"))) 21)))))

(deftest else-if--test
  (testing "with non-exception argument"
    (testing "and exception class specification"
      (is (= 42 (f/else-if NullPointerException
                           (constantly "caught")
                           42))))

    (testing "and non-exception class specification"
      (is (= "hello" (f/else-if String
                                (constantly "caught")
                                "hello")))))

  (testing "with exception argument"
    (testing "and class specification equal to exception class"
      (is (= "caught" (f/else-if NullPointerException
                                 (constantly "caught")
                                 (NullPointerException. "oops")))))

    (testing "and class specification non-equal to exception class"
      (let [err (UnsupportedOperationException. "oops")]
        (is (= err (f/else-if NullPointerException
                              (constantly "caught")
                              err)))))

    (testing "and non-exception class specification"
      (let [err (UnsupportedOperationException. "oops")]
        (is (= err (f/else-if String
                              (constantly "caught")
                              err)))))))

(deftest flet--test
  (testing "with no exception"
    (is (= 6 (f/flet [x (+ 1 2), y (+ x 3)] y)))
    (is (= 6 (f/flet [{:keys [x y]} {:x 2 :y 3}] (* x y)))))

  (testing "with exception in bindings"
    (is (f/fail? (f/flet [x (+ 1 2), y (/ x 0)] y))))

  (testing "with error returned in bindings"
    (let [err (ex-info "oops" {})]
      (is (= err (f/flet [x (+ 1 2), y err] x)))))

  (testing "with exception in body"
    (is (f/fail? (f/flet [x (+ 1 2), y 0] (/ x y)))))

  (testing "error returned in body"
    (let [err (ex-info "oops" {})]
      (is (= err (f/flet [x (+ 1 2), y 0] err)))))

  (testing "duplicate field name and signature error"
    (is (= 3 (f/flet [a_b 1 a-b 2 c 3] (+ a-b a_b))))))

(deftest catch-protocol--test
  (extend-protocol f/Catch
    NullPointerException
    (caught [t] (throw t)))
  (is (f/fail? (f/call 1)))
  (is (thrown? NullPointerException (f/call + 1 nil)))
  (is (thrown? NullPointerException (f/flet [x 1 y nil] (+ x y)))))

(deftest flow-protocol--test
  (is (= 2 (f/?ok (Right. 1) inc)))
  (is (= (Right. 1) (f/?err (Right. 1) (constantly :error))))
  (is (= (Right. 1) (f/?throw (Right. 1))))
  (is (= (Left. "oops") (f/?ok (Left. "oops") inc)))
  (is (= "oops" (f/?err (Left. "oops") #(-> % .getData :error))))
  (is (thrown? clojure.lang.ExceptionInfo (f/?throw (Left. "oops")))))
