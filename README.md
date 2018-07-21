# flow [![CircleCI](https://circleci.com/gh/dawcs/flow/tree/master.svg?style=svg)](https://circleci.com/gh/dawcs/flow/tree/master)

Handling exceptions in functional way

## Usage

### Pipeline

Consider (not really) trivial example:
```clojure
(try
  (next-dangerous-action (dangerous-action arg)))
  (catch Exception _
    (try (dangerous-fallback-action)
      (catch Exception _ default-value))))
```
Not too readable. Let's add some flow to it:

```clojure
(requre '[dawcs.flow :refer :all])

(->> (call dangerous-action arg)
     (then next-dangerous-action)
     (else dangerous-fallback-action)
     (either default-value))
```

Flow follows core Clojure idea of "everything is data" and provides exception toolset based on idea of errors as data. **call** is starting point to flow, it accepts a function and its arguments, wraps function call to try/catch block and returns either caught exception instance or call result:
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

**either** works similar to `else`, but accepts another value (default) and returns it in case of exception given as first agrument, otherwise it returns first agrument, so `(either default x)` is just a sugar for `(else (constantly default) x)`
```clojure
(->> (call / 1 0) (either 2)) ;; => 2
(->> (call / 0 1) (either 2)) ;; => 0
```

Other useful functions are `raise` and `thru`:

**raise** accepts 1 agrument and in case of exception given, throws it, otherwise simply returns given argument:
```clojure
(->> (call / 1 0) raise)) ;; throws ArithmeticException
(->> (call / 0 1) raise)) ;; => 1
```

**thru** accepts value and function, and applies function to value if value is an exception and return given value, so `(thru println x)` can be written as `(else #(doto % println) x)`, so function is called only for side-effects(like error logging).

**IMPORTANT** `thru` doesn't wrap handler to try/catch by default, so you should do that manually if you need that

As all described functions accept value as last agrument, they are ideal for `->>` macro or `partial` usage. But there are also variations for `->`: `then>`, `else>`, `either>` and `thru>`.

Another "real-life" example:

```clojure
;; some example dummy code
(defn find-entity [id]
  (if (some? id)
    {:id id :name "Jack" :role :admin}
    (f/fail "User not found" {:id id})))

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

`call` catches `java.lang.Throwable` by default, which may be not what you need, so this behavior can be changed globally by altering `*exception-base-class`:
```clojure
(alter-var-root #'*exception-base-class* (constantly java.lang.Exception))
;; there's also a helper for that
(catch-from! java.lang.Exception)
```
or locally using `catching` macro:
```clojure
(catching java.lang.Exception (call / 1 0))
```
if you don't need to catch some exceptions which may point to bad code(like `clojure.lang.ArityException`) and is better to find it as early as possible, it may be added to ignored exceptions list:
```clojure
(alter-var-root #'*ignored-exceptions* (constantly #{IllegalArgumentException ClassCastException}))
;; there's also a helper for that
(ignore-exceptions! #{IllegalArgumentException ClassCastException})
;; add without overwriting previous values
(add-ignored-exceptions! #{NullPointerException})
```
or defined for a block of code:
```clojure
(ignoring #{clojure.lang.ArityException} (call fail))
```

For more info look https://dawcs.github.io/flow


## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
