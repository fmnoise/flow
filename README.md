# flow

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
Not too readable, maybe threading macro can help?

```clojure
(-> (dangerous-action)
    (next-dangerous-action)
    (try catch Exception e (fallback-action e))
    (try catch Exception _ default-value))
```

Looks more readable, but still clunky.
Let's add some flow to it:

```clojure
(require '[flow.core as f])

(->> (f/call (dangerous-action))
     (f/then next-dangerous-action)
     (f/else dangerous-fallback-action)
     (f/either default-value))
```
`call` is starting point to flow, it's a macro which wraps given code to try/catch block and returns either caught exception instance or block evaluation result, each next flow function works with exception instance as a value, so instead of throwing it, it just returns it:

`then` applies its first agrument(function) to its second agrument(value) if value is not an exception, otherwise it just returns that exception.

`else` works as opposite, simply returning non-exception values and applying given function to value in case of exception.

`either` works similar to else, but accepts another value (default) and returns it in case of exception given as first agrument, otherwise it returns first agrument.

Another useful functions are `raise` and `thru`:
`raise` accepts 1 agrument and in case of exception given, throws it, otherwise simply returns given argument.

`thru` accepts value and function, and applies function to value if value is an exception. Difference with `else` is that `thru` always returns given value, so function is called only for side-effects(like error logging).


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
  (->> (f/call (find-entity id))
       (f/then #(update-entity % updates))
       (f/then format-response)
       (f/thru notify-slack)
       (f/else (comp format-error Throwable->map))))

(persist-changes 123 {:department "IT"})
;; => {:status 200, :entity {:id 123, :name "Jack", :role :admin, :department "IT"}}

(persist-changes nil {:department "IT"})
;; => {:status 500, :error "User not found", :context {:id nil}}
```

This example uses `fail` - simple wrapper around Clojure's core `ex-info` which allows to call it with single argument(passing empty map as second one).

And final secret weapon of `flow` is `flet` - exception-aware version of Clojure `let`.
Let's rewrite previous example:

```clojure
(defn perform-update [id updates]
  (f/flet [entity (find-entity id)
           updated-entity (update-entity entity data)]
    (format-response updated-entity)))

(defn persist-changes [id updates]
  (->> (perform-update id updates)
       (f/thru notify-slack)
       (f/else (comp format-error Throwable->map))))
```

That's it! More info in docstrings.


## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
