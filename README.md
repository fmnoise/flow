# flow [![CircleCI](https://circleci.com/gh/fmnoise/flow/tree/master.svg?style=svg)](https://circleci.com/gh/fmnoise/flow/tree/master) [![cljdoc badge](https://cljdoc.xyz/badge/dawcs/flow)](https://cljdoc.xyz/d/dawcs/flow/CURRENT)

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
Looks ugly enough? Don't panic, let's add some flow:
```clojure
(require '[dawcs.flow :refer [then else fail]])

(defn handler [{:keys [id user params]} db]
  (->> (or user (fail {:error "Login required" :code 401}))
       (then (fn [_] (or id (fail {:error "Missing entity id" :code 400}))))
       (then (fn [_] (or (get db id) (fail {:error "Entity not found" :code 404}))))
       (then (fn [_] (or (accessible? db id user) (fail {:error "User cannot update entity" :code 403}))))
       (then (fn [_] (update! db id params)))
       (else ex-data)))
```

### Basic blocks

Let's see what's going on here:

**fail** is a small wrapper around `clojure.core/ex-info` which allows to call it with zero or single argument.

**then** accepts value and a function, if value is not an exception instance, it calls function on it, returning result, otherwise it returns given exception instance.

**else** works as opposite, simply returning non-exception values and applying given function to exception instance values. There's also a syntax-sugar version - **else-if**. It accepts exception class as first agrument, making it pretty useful as functional `catch` branches replacement:
```clojure
(->> (call / 1 0)
     (then inc) ;; bypassed
     (else-if ArithmeticException (constantly :bad-math))
     (else-if Throwable (constantly :unknown-error))) ;; this is also bypassed cause previous function will return normal value
```

Ok, that looks simple and easy, but what if `update!` or any other function will throw exception instead of returning `fail`?
`then` is designed to catch all exceptions(starting from `Throwable` but that can be changed, more details soon) and return their instances so any thrown exception will be caught and passed through chain.
If we need to start a chain with something which can throw an exception, we should use **call** instead of `then`. `call` accepts a function and its arguments, wraps function call to `try/catch` block and returns either caught exception instance or function call result, example:
```clojure
(->> (call / 1 0) (then inc)) ;; => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (then inc)) ;; => 1
```

If we need to pass both cases (exception instances and normal values) through some function, **thru** is right tool. It works similar to `doto` but accepts function as first argument. It always returns given value, so supplied function is called only for side-effects(like error logging or cleaning up):
```clojure
(->> (call / 1 0) (thru println)) ;; => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (thru println)) ;; => 0
```
`thru` may be used similarly to `finally`, despite it's not exactly the same.

And a small cheatsheet to summarize on basic blocks:

![cheatsheet](https://raw.githubusercontent.com/dawcs/flow/master/doc/flow.png)

**IMPORTANT!** `then` uses `call` under the hood to catch exception instances. `else` and `thru` don't wrap handler to `call`, so you should do it manually if you need that.

### Early return

Having in mind that `then` will catch exceptions and return them immediately, throwing `fail` may be used as replacement for `return`:
```clojure
(->> (call get-objects)
     (then (partial map
                    (fn [obj]
                      (if (unprocessable? obj)
                        (throw (fail "Unprocessable object" {:object obj}))
                        (calculate-result object))))))

```
`(throw (fail ...))` in this example may be replaced with **fail!** which accepts the same arguments as `fail` and throws constructed exception.
Another case where early return may be useful is `let`:
```clojure
(defn assign-manager [report-id manager-id]
  (->> (call
         (fn []
           (let [report (or (db-find report-id) (fail! "Report not found"))
                 manager (or (db-find manager-id) (fail! "Manager not found"))]
             {:manager manager :report report})))
       (then db-persist))
       (else log-error)))
```
Wrapping function to `call` and throwing inside `let` in order to achieve early return may look ugly and verbose, so `flow` has own version of let - `flet`, which wraps all evaluations to `call`. In case of returning `fail` during bindings or body evaluation, it's immediately returned, otherwise it works as normal `let`:
```clojure
(flet [a 1 b 2] (+ a b)) ;; => 3
(flet [a 1 b (fail "oops")] (+ a b)) ;; => #error { :cause "oops" ... }
(flet [a 1 b 2] (fail "oops")) ;; => #error { :cause "oops" ... }
(flet [a 1 b (throw (Exception. "boom"))] (+ a b)) ;; => #error { :cause "boom" ... }
(flet [a 1 b 2] (throw (Exception. "boom"))) ;; => #error { :cause "boom" ... }
```
So previous example can be simplified:
```clojure
(defn assign-manager [report-id manager-id]
  (->> (flet [report (or (db-find report-id) (fail "Report not found"))
              manager (or (db-find manager-id) (fail "Manager not found"))]
         {:manager manager :report report})
       (then db-persist)
       (else log-error)))
```

### Tuning exceptions catching

`call` catches `java.lang.Throwable` by default, which may be not what you need, so this behavior can be changed:
```clojure
;; global override
(catch-from! java.lang.Exception)

;; dynamically define for a block of code
(catching java.lang.Exception (call / 1 0))
```
Some exceptions (like `clojure.lang.ArityException`) signal about bad code or typo and throwing them helps to find it as early as possible, while catching may lead to obscurity and hidden problems. In order to prevent catching them by `call`, certain exception classes may be added to ignored exceptions list:
```clojure
;; global override
(ignore-exceptions! #{IllegalArgumentException ClassCastException})

;; add without overwriting previous values
(add-ignored-exceptions! #{NullPointerException})

;; dynamically define for a block of code
(ignoring #{clojure.lang.ArityException} (call inc))
```

## Status

API is considered stable since version `1.0.0`. See changelog for the list of breaking changes.

## Who’s using Flow?

- [Eventum](https://eventum.no) - connects event organizers with their dream venue

## Acknowledgements

Thanks to Scott Wlaschin for his inspiring talk about Railway Oriented Programming
https://fsharpforfunandprofit.com/rop/

## License

Copyright © 2018 fmnoise

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
