# flow [![CircleCI](https://circleci.com/gh/dawcs/flow/tree/master.svg?style=svg)](https://circleci.com/gh/dawcs/flow/tree/master)

## Usage

[![Current Version](https://clojars.org/dawcs/flow/latest-version.svg)](https://clojars.org/dawcs/flow)

### Motivation

Consider trivial example:
```clojure
(defn handler [req db]
  (if-let [user (:user req)]
    (if-let [id (:id req)]
      (if (get db id)
        (if (accessible? db id user)
          (update! db id (:params req))
          {:error "Access denied" :code 403})
        {:error "Entity not found" :code 404})
      {:error "Missing entity id" :code 400})
    {:error "Login required" :code 401}))
```
Looks ugly enough? How about extracting each check to function?

```clojure
(defn check-login [req next]
  (if (:user req)
    next
    {:error "Login required"  :code 401}))

(defn check-id [req next]
  (if (:id req)
    next
    {:error "Missing entity id" :code 400}))

(defn check-entity [req db next]
  (if (get db (:id req))
    next
    {:error "Entity not found" :code 404}))

(defn check-access [req db next]
  (if (accessible? db (:id req) (:user req))
    next
    {:error "Access denied" :code 403}))

(defn update-entity [req db]
  (update! db (:id req) (:params req)))

(defn handler [req db]
  (check-login
   req
   (check-id req
             (check-entity req db
                           (check-access req db (upate-entity db req))))))
```
Hmm, that haven't made it better. Adding threading macro (for readability) adds obscurity instead due to reversed order:
```clojure
(defn handler [req db]
  (->> (upate-entity req db)
       (check-access req db)
       (check-entity req db)
       (check-id req)
       (check-login req)))
```
Ok, don't panic, let's add some flow:
```clojure
(require '[dawcs.flow :refer [then else fail fail-data]])

(defn handler [{:keys [id user params]} db]
  (->> (or user (fail {:error "Login required" :code 401}))
       (then (fn [_] (or id (fail {:error "Missing entity id" :code 400}))))
       (then (fn [_] (or (get db id) (fail {:error "Entity not found" :code 404}))))
       (then (fn [_] (or (accessible? db id user) (fail {:error "User cannot update entity" :code 403}))))
       (then (fn [_] (update! db id params)))
       (else fail-data)))
```

### Basic blocks

Let's see what's going on here:

`fail` is just a small wrapper around Clojure's core `ex-info` which allows to call it with single argument

`then` accepts value and a function, if value is not an exception instance, it calls function on it, returning result, otherwise it returns given exception instance

`else` works as opposite, simply returning non-exception values and applying given function to exception instance values

`fail-data` is also small helper for extracting data passed to ex-info

Ok, that looks simple and easy, but what if `update!` or any other function will throw real Exception?
`then` is designed to catch all exceptions and return their instances so any exception will go through chain correctly.
If we need to start a chain with something which can throw an exception, we should use `call`. `call` accepts a function and its arguments, wraps function call to `try/catch` block and returns either caught exception instance or function call result, example:
```clojure
(call / 1 0) => #error {:cause "Divide by zero" :via ...}
(call / 0 1) => 0
```

`else` has also a syntax-sugar version: `else-if`, it accepts exception class as first agrument, making it pretty useful as functional `catch` branches replacement:
```clojure
(->> (call / 1 0)
     (then inc) ;; bypassed
     (else-if ArithmeticError :bad-math)
     (else-if Throwable :unknown-error)) ;; this is also bypassed cause previous function will return normal value
```

If we need to pass both cases (exception instances and normal values) through some function, `thru` is right tool. `thru` works similar to `doto` but accepts function as first argument, so supplied function is called only for side-effects(like error logging or cleaning up):
```clojure
(->> (call / 1 0) (thru println)) => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (thru println)) => 0
```
`thru` may be used similarly to `finally`, despite it's not exactly the same.

**IMPORTANT!** `then` uses `call` under the hood to catch exception instances. `else` and `thru` don't wrap handler to `call`, so you should do it manually if you need that.

Having in mind that `then` will catch exceptions and return them immediately, throwing `fail` may be used as replacement for `return`:
```clojure
(->> (call get-objects)
     (then (partial map
                    (fn [obj]
                      (if (unprocessable? obj)
                        (throw (fail "Unprocessable object" {:object obj}))
                        (calculate-result object))))))

```

Another example where early return may be useful is `let`:
```clojure
(defn assign-manager [db report-id manager-id]
  (->> (call
         (fn []
           (let [report (->> (call db-find db report-id) (else (throw %)))
                 manager (->> (call db-find db manager-id) (else (throw %)))]
             {:manager manager :report report})))
       (then #(store-to-db %))
       (else log-error)))
```
Wrapping function to `call` and throwing inside `let` in order to achieve early return in case of failure may look ugly and verbose, so `flow` has own version of let - `flet`, which wraps all evaluations to `call`. In case returning `fail` during bindings or body evaluation, it's immediately returned, otherwise it works as normal `let`:
```clojure
(flet [a 1 b 2] (+ a b)) ;; => 3
(flet [a 1 b (fail "oops")] (+ a b)) ;; => #error { :cause "oops" ... }
(flet [a 1 b 2] (fail "oops")) ;; => #error { :cause "oops" ... }
(flet [a 1 b (throw (Exception. "boom"))] (+ a b)) ;; => #error { :cause "boom" ... }
(flet [a 1 b 2] (throw (Exception. "boom"))) ;; => #error { :cause "boom" ... }

(defn assign-manager [db report-id manager-id]
  (->> (flet [report (call db-find db report-id)
              manager (call db-find db manager-id)]
         {:manager manager :report report})
       (then #(store-to-db %))
       (else log-error)))
```

### Tuning exception catching

`call` catches `java.lang.Throwable` by default, which may be not what you need, so this behavior can be changed:
```clojure
(catch-from! java.lang.Exception)

;; dynamically define for a block of code
(catching java.lang.Exception (call / 1 0))
```
Some exceptions (like `clojure.lang.ArityException`) may signal about bad code or typo and throwing them may help to find it as early as possible, so it may be added to ignored exceptions list, so all pipeline functions will not catch these exceptions and it will throw:
```clojure
(ignore-exceptions! #{IllegalArgumentException ClassCastException})

;; add without overwriting previous values
(add-ignored-exceptions! #{NullPointerException})

;; dynamically define for a block of code
(ignoring #{clojure.lang.ArityException} (call fail))
```

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
