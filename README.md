# flow [![CircleCI](https://circleci.com/gh/fmnoise/flow/tree/master.svg?style=svg)](https://circleci.com/gh/fmnoise/flow/tree/master) [![cljdoc badge](https://cljdoc.xyz/badge/dawcs/flow)](https://cljdoc.xyz/d/dawcs/flow/CURRENT)

## Usage

[![Current Version](https://clojars.org/dawcs/flow/latest-version.svg)](https://clojars.org/dawcs/flow)

### Motivation

Consider trivial example:
```clojure
(defn update-handler [req db]
  (if-let [user (:user req)]
    (if-let [id (:id req)]
      (if-let [entity (fetch-entity db id)]
        (if (accessible? entity user)
          (update-entity! entity (:params req))
          {:error "Access denied" :code 403})
        {:error "Entity not found" :code 404})
      {:error "Missing entity id" :code 400})
    {:error "Login required" :code 401}))
```
Looks ugly enough? Let's add some readability. First, require flow:
```clojure
(require '[dawcs.flow :refer [then else]])
```
Then let's extract each check to function to make code more clear and testable(notice using `ex-info` as error container with ability to store map with some data in addition to message):
```clojure
(defn check-user [req]
  (or (:user req)
    (ex-info "Login requred" {:code 401})))

(defn check-entity-id [req]
  (or (:id req)
    (ex-info "Missing entity id" {:code 400})))

(defn check-entity-exists [db id]
  (or (fetch-entity db id)
    (ex-info "Entity not found" {:code 404})))

(defn check-entity-access [entity user]
  (if (accessible? entity user)
    entity
    (ex-info "Access denied" {:code 403})))
```
Then let's add error formatting helper to turn ex-info data into desired format:
```clojure
(defn format-error [^Throwable err]
  (assoc (ex-data err)
         :error (.getMessage err))) ;; ex-message in clojure 1.10 can be used instead
```
And finally we can write pretty readable pipeline(notice thread-last macro usage):
```clojure
(defn update-handler [req db]
  (->> (check-user req)
       (then (fn [_] (check-entity-id req))
       (then #(check-entity-exists db %))
       (then #(check-entity-access % (:user req))
       (then #(update-entity! % (:params req))))
       (else format-error)))
```

### Basic blocks

Let's see what's going on here:

**then** accepts value and a function, if value is not an exception instance, it calls function on it, returning result, otherwise it returns given exception instance.

**else** works as opposite, simply returning non-exception values and applying given function to exception instance values. There's also a syntax-sugar version - **else-if**. It accepts exception class as first agrument, making it pretty useful as functional `catch` branches replacement:
```clojure
(->> (call / 1 0)
     (then inc) ;; bypassed
     (else-if ArithmeticException (constantly :bad-math))
     (else-if Throwable (constantly :unknown-error))) ;; this is also bypassed cause previous function will return normal value
```

**call** is functional `try/catch` replacement designed to catch all exceptions(starting from `Throwable` but that can be changed, more details soon) and return their instances so any thrown exception will be caught and passed through chain. `call` accepts a function and its arguments, wraps function call to `try/catch` block and returns either caught exception instance or function call result, example:
```clojure
(->> (call / 1 0) (then inc)) ;; => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (then inc)) ;; => 1
```

Using `call` inside `then` may look verbose:
```clojure
(->> (rand-int 10) ;; some calculation which may return 0
     (then (fn [v] (call #(/ 10 v))) ;; can cause "Divide by zero" so should be inside call
```
so there's **then-call** for it (and **else-call** also exists for consistency)
```clojure
(->> (rand-int 10)
     (then-call #(/ 10 %)))
```

If we need to pass both cases (exception instances and normal values) through some function, **thru** is right tool. It works similar to `doto` but accepts function as first argument. It always returns given value, so supplied function is called only for side-effects(like error logging or cleaning up):
```clojure
(->> (call / 1 0) (thru println)) ;; => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (thru println)) ;; => 0
```
`thru` may be used similarly to `finally`, despite it's not exactly the same.

And a small cheatsheet to summarize on basic blocks:

![cheatsheet](https://raw.githubusercontent.com/dawcs/flow/master/doc/flow.png)



### Early return

Having in mind that `call` will catch exceptions and return them immediately, throwing exception may be used as replacement for `return`:
```clojure
(->> (call get-objects)
     (then-call (partial map
                  (fn [obj]
                    (if (unprocessable? obj)
                      (throw (ex-info "Unprocessable object" {:object obj}))
                      (calculate-result object))))))

```
Another case where early return may be useful is `let`:
```clojure
(defn assign-manager [report-id manager-id]
  (->> (call
         (fn []
           (let [report (or (db-find report-id) (throw (ex-info "Report not found" {:id report-id})))
                 manager (or (db-find manager-id) (throw (ex-info "Manager not found" {:id manager-id})))]
             {:manager manager :report report})))
       (then db-persist))
       (else log-error)))
```
Wrapping function to `call` and throwing inside `let` in order to achieve early return may look ugly and verbose, so `flow` has own version of let - `flet`, which wraps all evaluations to `call`. In case of returning exception instance during bindings or body evaluation, it's immediately returned, otherwise it works as normal `let`:
```clojure
(flet [a 1 b 2] (+ a b)) ;; => 3
(flet [a 1 b (ex-info "oops" {:reason "something went wrong"})] (+ a b)) ;; => #error { :cause "oops" ... }
(flet [a 1 b 2] (Exception. "oops")) ;; => #error { :cause "oops" ... }
(flet [a 1 b (throw (Exception. "boom"))] (+ a b)) ;; => #error { :cause "boom" ... }
(flet [a 1 b 2] (throw (Exception. "boom"))) ;; => #error { :cause "boom" ... }
```
So previous example can be simplified:
```clojure
(defn assign-manager [report-id manager-id]
  (->> (flet [report (or (db-find report-id) (ex-info "Report not found" {:id report-id}))
              manager (or (db-find manager-id) (ex-info "Manager not found" {:id manager-id}))]
         {:manager manager :report report})
       (then db-persist)
       (else log-error)))
```

### Tuning exceptions catching

`call` catches `java.lang.Throwable` by default, which may be not what you need, so this behavior can be changed:
```clojure
(catch-from! java.lang.Exception)
```
Some exceptions (like `clojure.lang.ArityException`) signal about bad code or typo and throwing them helps to find it as early as possible, while catching may lead to obscurity and hidden problems. In order to prevent catching them by `call`, certain exception classes may be added to ignored exceptions list:
```clojure
(ignore-exceptions! #{IllegalArgumentException ClassCastException})

;; add without overwriting previous values
(add-ignored-exceptions! #{NullPointerException})
```
These methods are using mutation of dynamic variables and can be used during system startup to perform global change, but if you need to change behavior in certain block of code(or you simply want more functional approach without involving global mutable state) there's **call-with** which works similar to `call` but its first argument is handler - function which is called on caught exception:
```clojure
(defn handler [e]
  (if (instance? clojure.lang.ArityException) (throw e) e))

(call-with handler inc) ;; throws ArityException, as inc requires more than 1 argument
```
Using multimethods/protocols we can achieve full power of fine-tuning what to catch and return as exception instance and what to throw:
```clojure
(defprotocol ErrorHandling
  (handle [e]))

;; let's say we want to catch everything starting from Exception but throw NullPointerException
(extend-protocol ErrorHandling
  Throwable
  (handle [e] (throw e))
  Exception
  (handle [e] e)
  NullPointerException
  (handle [e] (throw e)))

(call-with handle + 1 nil) ;; throws NullPointerException
```

Custom handler may be also passed to `flet` in first pair of binding vector:
```clojure
;; this flet works the same as let if exception occured
(flet [:handler #(throw %)
       a 1
       b (/ a 0)]
  (+ a b)) ;; throws ArithmeticException

;; but it can do early return if exception is returned as value
(flet [:handler #(throw %)
       a 1
       b (ex-info "Something went wrong" {:because "Monday"})]
  (/ a b)) ;; => #error {:cause "Something went wrong" :data {:because "Monday"} ... }
```

## How it's different from Either?

The core idea of `flow` is clear separation of normal value(everything which is not exception instance) and value which indicates error(exception instance) without involving additional containers. This allows to get rid of redundant abstractions like `Either`, and also prevents mess with value containers (if you've ever seen `Either.Left` inside `Either.Right` you probably know what I'm talking about). Exceptions are already first-class citizens in Java world but are usually combined with side-effect (throwing) for propagation purposes, while `flow` actively promotes more functional usage of it with returning exception instance:
```clojure
;; construction
(ex-info "User not found" {:id 123})

;; catching and returning instance
(try (/ 1 0) (catch Exception e e))
```
In both examples above we clearly understand that returned value is an error, so there's no need to wrap it to any other container like `Either`(also, Clojure's core function `ex-info` is perfect tool for storing additional data in exception instance and it's already available from the box). That means no or minimal rework of existing code in order to get started with `flow`, while `Either` would need wrapping both normal and error values into its corresponding `Right` and `Left` containers. Due to described features `flow` is much easier to introduce into existing project than `Either`.

### But isn't using exceptions costly?

In some of examples above exception instance is constructed and passed through chain without throwing. That's main use-case and ideology of flow - using exception instance as error value. But we know that constructing exception is costly as it fills the stacktrace, but we may not use it.  Java 7 has a possibility to omit stacktrace creation, but that change to ExceptionInfo was not accepted by the core team (more details [here](https://clojure.atlassian.net/browse/CLJ-2423)) so we ended up creating custom exception class which implements `IExceptionInfo` but can skip stacktrace creation. It's called `Fail` and there's handly constuctor for it:
```clojure
(fail-with {:msg "User not found" :data {:id 1}}) ;; => #error {:cause "User not found" :data {:id 1} :via [...] :trace []}

;; it behaves the same as ExceptionInfo
(ex-data *1) ;; => {:id 1}

;; map may be empty or nil
(fail-with nil) ;; => #error {:cause nil :data {} :via [...] :trace []}

;; stacktrace is disabled by default but can be turned on
(fail-with {:msg "User not found" :data {:id 1} :trace? true})

;; there's also throwing constuctor (stacktrace is enabled by default)
(fail-with! {:msg "User not found" :data {:id 1}})
```

## Status

API is considered stable since version `1.0.0`. See changelog for the list of breaking changes.

## Who’s using Flow?

- [Eventum](https://eventum.no) - connects event organizers with their dream venue
- [Yellowsack](https://yellowsack.com) - dumpster bag & and pick up service

## Acknowledgements

Thanks to Scott Wlaschin for his inspiring talk about Railway Oriented Programming
https://fsharpforfunandprofit.com/rop/

## License

Copyright © 2018 fmnoise

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
