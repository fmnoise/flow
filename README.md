# flow [![CircleCI](https://circleci.com/gh/dawcs/flow/tree/master.svg?style=svg)](https://circleci.com/gh/dawcs/flow/tree/master)

## Usage

[![Current Version](https://clojars.org/dawcs/flow/latest-version.svg)](https://clojars.org/dawcs/flow)

Consider (not really) trivial example:
```clojure
(try
  (next-dangerous-action (dangerous-action arg)))
  (catch Exception e
    (log-error "action failed" e)
    (try (dangerous-fallback-action)
      (catch Exception e
        (log-error "fallback action failed" e)
        default-value))))
```
Not too readable. Let's add some `flow` to it:

```clojure
(requre '[dawcs.flow :refer :all])

(->> (call dangerous-action arg)
     (then next-dangerous-action)
     (else (fn [err]
             (log-error "action failed" err)
             (call dangerous-fallback-action)))
     (else (fn [err]
             (log-error "fallback action failed" %)
             default-value)))
```

`call` is starting point to `flow`, it accepts a function and its arguments, wraps function call to `try/catch` block and returns either caught exception instance or call result:
```clojure
(call / 1 0)
 => #error {
     :cause "Divide by zero"
     :via
     [{:type java.lang.ArithmeticException
       :message "Divide by zero"
       :at [clojure.lang.Numbers divide "Numbers.java" 158]}]
     :trace ...}

(call / 0 1) ;; => 0
```

Each next `flow` function works with exception instance as a value, so instead of throwing it, it just returns it:

`then` applies its first agrument(function) to its second agrument(value) if value is not an exception, otherwise it just returns that exception:
```clojure
(->> (call / 1 0) (then inc)) => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (then inc)) => 1
```

`else` works as opposite, simply returning non-exception values and applying given function to value in case of exception:
```clojure
(->> (call / 1 0) (else (comp :cause Throwable->map))) => "Divide by zero"
(->> (call / 0 1) (else (comp :cause Throwable->map))) => 0
```

`thru` works similar to `doto` but accepts function as first argument, so supplied function is called only for side-effects(like error logging or cleaning up):
```clojure
(->> (call / 1 0) (thru println)) => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (thru println)) => 0
```

**IMPORTANT!** `then` uses `call` under the hood, so if handler fails, error will be caught and passed through rest of pipeline. `else` and `thru` don't wrap handler to `call`, so you should do it manually if you need that behavior.

More real-life example:

```clojure
;; some example dummy code
(defn find-entity [id]
  (if (some? id)
    {:id id :name "Jack" :role :admin}
    (fail "User not found" {:id id})))

(defn update-entity [entity data]
  (merge entity data))

(defn notify-slack [err]
  (prn "Slack notified:" err))

(defn format-response [data]
  {:status 200 :entity data})

(defn format-error [{:keys [cause data]}]
  {:status 500 :error cause :context data})

;; pipeline
(defn persist-changes [id updates]
  (->> (call find-entity id)
       (then #(update-entity % updates))
       (then format-response)
       (else (fn [err]
               (->> err
                    (thru notify-slack)
                    Throwable->map
                    format-error)))))

(persist-changes 123 {:department "IT"})
;; => {:status 200, :entity {:id 123, :name "Jack", :role :admin, :department "IT"}}

(persist-changes nil {:department "IT"})
;; => {:status 500, :error "User not found", :context {:id nil}}
```

### fail

Example above uses `fail` - simple wrapper around Clojure's core `ex-info` which allows to call it with single argument(passing empty map as second one). In addition there's `fail?` which checks if given value class is subclass of `Throwable`.
Besides being a helper for constructing `ex-info`, `fail` is perfect tool for propagating errors. Function can simply return `fail` as a signal that something went wrong during processing, so `then/else/thru` will process it correctly.
```clojure
(defn ratio [value total]
 (if (pos-int? total)
   (/ value total)
   (fail "Total should be positive int")))

(->> (ratio 1 0)
     (then inc)
     (thru prn)
     (else (constantly 0))) ;; => 0

(->> (ratio 0 1)
     (then inc)
     (thru prn)
     (else (constantly 0))) ;; => 1
```
Throwing ex-info instance may be also used as replacement for `return`:

```clojure
(->> (call get-objects)
     (then (partial map
                    (fn [obj]
                      (if (unprocessable? obj)
                        (throw (fail "Unprocessable object" {:object obj}))
                        (calculate-result object))))))

```

### flet

`flet` is exception-aware version of Clojure `let`. In case of exception thrown in bindings or body, it returns its instance, otherwise returns evaluation result.
Let's rewrite previous example:

```clojure
(defn perform-update [id updates]
  (flet [entity (find-entity id)
         updated-entity (update-entity entity updates)]
    (format-response updated-entity)))

(defn persist-changes [id updates]
  (->> (perform-update id updates)
       (thru notify-slack)
       (else (comp format-error Throwable->map))))
```

### Tuning exceptions

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

## FAQ

Q: Is there an alternative to `finally` clause provided by flow?

A: No, `finally` is currently not implented. Consider using `try/catch/finally` if you need that.


Q: How about cljs support?

A: cljs is not supported at the moment. Feel free to open PR if you need it.

## API docs

https://dawcs.github.io/flow

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
