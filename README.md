# flow [![CircleCI](https://circleci.com/gh/dawcs/flow/tree/master.svg?style=svg)](https://circleci.com/gh/dawcs/flow/tree/master)

Handling exceptions in functional way

## Usage

Flow is an exception handling toolbox, which follows core Clojure idea of "everything is data". So instead of trying/catching/throwing exceptions it provides tools for builing declarative handling chain.
Consider trivial example:
```clojure
(try
  (next-dangerous-action (dangerous-action)))
  (catch Exception e
    (try (dangerous-fallback-action e)
      (catch Exception _ default-value))))
```
Not too readable. Let's add some flow to it:

```clojure
(requre '[dawcs.flow :refer :all])

(->> (call (dangerous-action))
     (then next-dangerous-action)
     (else dangerous-fallback-action)
     (either default-value))
```

**call** is starting point to flow, it's a macro which wraps given code to try/catch block and returns either caught exception instance or block evaluation result, each next flow function works with exception instance as a value, so instead of throwing it, it just returns it:
```clojure
(call (/ 1 0))
 => #error {
     :cause "Divide by zero"
     :via
     [{:type java.lang.ArithmeticException
       :message "Divide by zero"
       :at [clojure.lang.Numbers divide "Numbers.java" 158]}]
     :trace ...}

(call (/ 0 1)) ;; => 0
```

`call` catches `java.lang.Throwable` by default, which may be not what you need, so this behavior can be changed globally by altering `*exception-base-class`:
```clojure
(alter-var-root #'*exception-base-class* (constantly java.lang.Exception))
```
or locally using `catching` macro:
```clojure
(catching java.lang.Exception (call (/ 1 0)))
```

**then** applies its first agrument(function) to its second agrument(value) if value is not an exception, otherwise it just returns that exception:
```clojure
(->> (call (/ 1 0)) (then inc)) => #error {:cause "Divide by zero" :via ...}
(->> (call (/ 0 1)) (then inc)) => 1
```

**else** works as opposite, simply returning non-exception values and applying given function to value in case of exception:
```clojure
(->> (call (/ 1 0)) (else (comp :cause Throwable->map))) => "Divide by zero"
(->> (call (/ 0 1)) (else (comp :cause Throwable->map))) => 0
```

**either** works similar to `else`, but accepts another value (default) and returns it in case of exception given as first agrument, otherwise it returns first agrument, so `(either default x)` is just a sugar for `(else (constantly default) x)`
```clojure
(->> (call (/ 1 0)) (either 2)) ;; => 2
(->> (call (/ 0 1)) (either 2)) ;; => 0
```

Other useful functions are `raise` and `thru`:

**raise** accepts 1 agrument and in case of exception given, throws it, otherwise simply returns given argument:
```clojure
(->> (call (/ 1 0) raise)) ;; throws ArithmeticException
(->> (call (/ 0 1) raise)) ;; => 1
```

**thru** accepts value and function, and applies function to value if value is an exception and return given value, so `(thru println x)` can be written as `(else #(doto % println) x)`, so function is called only for side-effects(like error logging).

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
  (->> (call (find-entity id))
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

And final secret weapon of `flow` is **flet** - exception-aware version of Clojure `let`.
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

For more info look https://dawcs.github.io/flow


## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
