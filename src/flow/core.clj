(ns flow.core)

;; TODO let, flow->, flow->>

(defn err? [value]
  (isa? (class value) java.lang.Throwable))

(defn fail
  ([msg] (fail msg {}))
  ([msg data] (ex-info msg data))
  ([msg data err] (ex-info msg data err)))

(defmacro call
  "Executes body in `try` block. If exception thrown during execution, returns it, otherwise returns value of body"
  [& body]
  `(try ~@body (catch java.lang.Throwable ~'e ~'e)))

(defn raise
  "If value is an exception, throws it, otherwise returns value"
  [value]
  (if (err? value) (throw value) value))

(defn then
  "If value is not an exception, applies f to it wrapped in `call`, otherwise returns value"
  [handler value]
  (if (err? value) value (call (handler value))))

(defn else
  "If value is an exception of ex-class(optional), applies handler to it wrapped in `call`, otherwise returns value"
  ([handler value]
   (if (err? value) (call (handler value)) value))
  ([ex-class handler value]
   (if (isa? (class value) ex-class) (call (handler value)) value)))

(defn either
  "If value is an exception, returns default, otherwise returns value"
  [default value]
  (if (err? value) default value))

(defn thru
  "If value is an exception of ex-class(optional), calls handler on it (for side effects). Returns value"
  ([handler value]
   (when (err? value) (handler value))
   value)
  ([ex-class handler value]
   (when (isa? (class value) ex-class) (handler value))
   value))

(defn then>
  "Value-first version of `then`"
  [value handler]
  (then handler value))

(defn else>
  "Value-first version of `else`"
  ([value handler]
   (else handler value))
  ([ex-class handler value]
   (else value ex-class handler)))

(defn either>
  "Value-first version of `either`"
  [value default]
  (either default value))

(defn thru>
  "Value-first version of `thru`"
  [value handler]
  (thru handler value))
