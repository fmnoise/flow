# flow

Handling exceptions in functional way

## Usage

```clojure
(require '[flow.core as f])

;; some example dummy code
(defn lookup-db [id]
  (throw (Exception. "Connection failed")))

(defn update-data [id data]
  ;; updates data
  )

(defn notify-slack [err]
 ;; notifies slack about error
 )

(defn format-response [data]
  {:status 200 :entity data})

;; pipeline
(defn update-entity [id data]
  (-> (f/run (lookup-db id))
      (f/then #(update-data % data))
      (f/then format-response)
      (f/thru notify-slack)
      (f/else #(-> {:status 500 :error (.getMessage %)}))
```

More docs soon

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
