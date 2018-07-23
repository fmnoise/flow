# flow [![CircleCI](https://circleci.com/gh/dawcs/flow/tree/master.svg?style=svg)](https://circleci.com/gh/dawcs/flow/tree/master)

Handling exceptions in functional way

## Usage

### Pipeline

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
Not too readable. Let's add some flow to it:

```clojure
(requre '[dawcs.flow :refer :all])

(->> (call dangerous-action arg)
     (then next-dangerous-action)
     (thru #(log-error "action failed" %))
     (else (fn [_] (call dangerous-fallback-action)))
     (thru #(log-error "fallback action failed" %))
     (else (constantly default-value)))
```

Flow follows core Clojure idea of "everything is data" and provides exception toolset based on idea of errors as data.

**call** is starting point to flow, it accepts a function and its arguments, wraps function call to try/catch block and returns either caught exception instance or call result:
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

Each next flow function works with exception instance as a value, so instead of throwing it, it just returns it:

**then** applies its first agrument(function) to its second agrument(value) if value is not an exception, otherwise it just returns that exception:
```clojure
(->> (call / 1 0) (then inc)) => #error {:cause "Divide by zero" :via ...}
(->> (call / 0 1) (then inc)) => 1
```

**else** works as opposite, simply returning non-exception values and applying given function to value in case of exception:
```clojure
(->> (call / 1 0) (else (comp :cause Throwable->map))) => "Divide by zero"
(->> (call / 0 1) (else (comp :cause Throwable->map))) => 0
```

**thru** works the same as `else` but always returns given value, so supplied function is called only for side-effects(like error logging):
```clojure
(->> (call / 1 0) (else println)) => nil
(->> (call / 1 0) (thru println)) => #error {:cause "Divide by zero" :via ...}
```

**IMPORTANT!** `then` uses `call` under the hood, so if handler fails, error will be caught and passed through rest of pipeline. `else` and `thru` doesn't wrap handler to `call`, so you should do it manually if you need that behavior.

Another "real-life" example:

```clojure
;; some example dummy code
(defn find-entity [id]
  (if (some? id)
    {:id id :name "Jack" :role :admin}
    (fail "User not found" {:id id})))

(defn update-entity [entity data]
  (merge entity data))

(defn notify-slack [err]
  (prn "Slack notified"))

(defn format-response [data]
  {:status 200 :entity data})

(defn format-error [{:keys [cause data]}]
  {:status 500 :error cause :context data})

;; pipeline
(defn persist-changes [id updates]
  (->> (call find-entity id)
       (then #(update-entity % updates))
       (then format-response)
       (thru notify-slack)
       (else (comp format-error Throwable->map))))

(persist-changes 123 {:department "IT"})
;; => {:status 200, :entity {:id 123, :name "Jack", :role :admin, :department "IT"}}

(persist-changes nil {:department "IT"})
;; => {:status 500, :error "User not found", :context {:id nil}}
```

This example uses **fail** - simple wrapper around Clojure's core `ex-info` which allows to call it with single argument(passing empty map as second one). In addition there are `fail!` which throws created `clojure.lang.ExceptionInfo` and `fail?` which checks if given value class is subclass of `Throwable`.

### flet

**flet** is exception-aware version of Clojure `let`. In case of exception thrown in bindings or body, it returns its instance, otherwise returns evaluation result.
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
(alter-var-root #'*exception-base-class* (constantly java.lang.Exception))

;; or using a helper
(catch-from! java.lang.Exception)

;; or define for a block of code
(catching java.lang.Exception (call / 1 0))
```
Some exceptions (like `clojure.lang.ArityException`) may signal about bad code or typo and throwing them may help to find it as early as possible, so it may be added to ignored exceptions list, so all pipeline functions will not catch these exceptions and it will throw:
```clojure
(alter-var-root #'*ignored-exceptions* (constantly #{IllegalArgumentException ClassCastException}))

;; there's also a helper for that
(ignore-exceptions! #{IllegalArgumentException ClassCastException})

;; add without overwriting previous values
(add-ignored-exceptions! #{NullPointerException})

;; or define for a block of code
(ignoring #{clojure.lang.ArityException} (call fail))
```

For more info look https://dawcs.github.io/flow


## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
