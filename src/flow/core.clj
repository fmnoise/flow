(ns flow.core)

;; TODO let, run->, run->>

(defn err? [value]
  (isa? (class value) java.lang.Throwable))

(defn fail
  ([msg] (fail msg {}))
  ([msg data] (ex-info msg data))
  ([msg data err] (ex-info msg data err)))

(defmacro run
  "Executes body in `try` block. If exception thrown during execution, returns it, otherwise returns value of body"
  [body]
  `(try ~body (catch java.lang.Throwable ~'e ~'e)))

(defn raise
  "If value is an exception, throws it, otherwise returns value"
  [value]
  (if (err? value) (throw value) value))

(defn then
  "If value is not an exception, applies f to it wrapped in `run`, otherwise returns value"
  [value f]
  (if (err? value) value (run (f value))))

(defn else
  "If value is an exception of ex-class(optional), applies handler to it wrapped in `run`, otherwise returns value"
  ([value handler]
   (if (err? value) (run (handler value)) value))
  ([value ex-class handler]
   (if (isa? (class value) ex-class) (run (handler value)) value)))

(defn either
  "If value is an exception, returns default, otherwise returns value"
  [value default]
  (if (err? value) default value))

(defn thru
  "If value is an exception of ex-class(optional), calls handler on it (for side effects). Returns value"
  ([value handler]
   (when (err? value) (handler value))
   value)
  ([value ex-class handler]
   (when (isa? (class value) ex-class) (handler value))
   value))

(defn then>>
  "Thread-last version of `then`"
  [f value]
  (then value f))

(defn else>>
  "Thread-last version of `else`"
  ([handler value]
   (else value handler))
  ([ex-class handler value]
   (else value ex-class handler)))

(defn either>>
  "Thread-last version of `either`"
  [default value]
  (either value default))

(defn thru>>
  "Thread-last version of `thru`"
  [handler value]
  (thru value handler))
