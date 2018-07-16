(ns dawcs.flow)

(def ^:dynamic *exception-base-class*
  "Base exception class which will be caught by `call`. Dynamic, defaults to `Throwable`"
  java.lang.Throwable)

(defn- ex-class-arg-check [ex-class]
  (when-not (isa? ex-class *exception-base-class*)
    (throw (IllegalArgumentException. (str "ex-class argument should be a subclass of " *exception-base-class* " but got " ex-class " instead")))))

(defn fail?
  "Checks if value is Throwable"
  [value]
  (isa? (class value) java.lang.Throwable))

(defn fail
  "Creates new `ex-info` instance with given msg, data(optional) and cause(optional)"
  ([msg] (fail msg {}))
  ([msg data] (ex-info msg (if (map? data) data {::context data})))
  ([msg data cause] (ex-info msg data cause)))

(defn fail!
  "Creates new `ex-info` instance with given msg, data(optional) and cause(optional) and throws it"
  ([msg] (fail! msg {}))
  ([msg data] (throw (ex-info msg (if (map? data) data {::context data}))))
  ([msg data cause] (throw (ex-info msg data cause))))

(defmacro call
  "Executes body in `try/catch/finally` block. When caught an exception
  which class is `*exception-base-class*`(defaults to `Throwable`) or a subclass of it,
  returns it, otherwise `throw`s it. Returns value of body if no exception has caught.
  `finally` block is optional and can be supplied by preceeding with :finally keyword, example:
  (call (/ 1 0) :finally (println :done))"
  [& body]
  (let [[try-body [_ & finally-body]] (split-with (complement #{:finally}) body)]
    `(try ~@try-body
          (catch java.lang.Throwable t#
            (if (isa? (class t#) *exception-base-class*)
              t#
              (throw t#)))
          (finally ~@finally-body))))

(defmacro catching
  "Executes body with `*exception-base-class*` bound to given class"
  [exception-base-class & body]
  `(binding [*exception-base-class* ~exception-base-class]
     ~@body))

(defn raise
  "If value is a `fail?`, throws it, otherwise returns value"
  [value]
  (if (fail? value) (throw value) value))

(defn then
  "If value is not a `fail?`, applies f to it, otherwise returns value"
  [handler value]
  (if (fail? value) value (handler value)))

(defn then-call
  "If value is not a `fail?`, applies f to it wrapped in `call`, otherwise returns value"
  [handler value]
  (if (fail? value) value (call (handler value))))

(defn else
  "If value is a `fail?`, applies handler to it, otherwise returns value"
  [handler value]
  (if (fail? value) (handler value) value))

(defn else-call
  "If value is a `fail?`, applies handler to it wrapped in `call`, otherwise returns value"
  [handler value]
  (if (fail? value) (call (handler value)) value))

(defn thru
  "If value is an `fail?`, calls handler on it (for side effects). Returns value"
  [handler value]
  (when (fail? value) (handler value))
  value)

(defn either
  "If value is a `fail?`, returns default, otherwise returns value"
  [default value]
  (if (fail? value) default value))

(defn else-if
  "If value is an exception of ex-class, applies handler to it, otherwise returns value"
  [ex-class handler value]
  (ex-class-arg-check ex-class)
  (if (isa? (class value) ex-class) (handler value) value))

(defn else-call-if
  "If value is an exception of ex-class, applies handler to it wrapped in `call`, otherwise returns value"
  [ex-class handler value]
  (ex-class-arg-check ex-class)
  (if (isa? (class value) ex-class) (call (handler value)) value))

(defn thru-if
  "If value is an exception of ex-class, calls handler on it (for side effects). Returns value"
  [ex-class handler value]
  (ex-class-arg-check ex-class)
  (when (isa? (class value) ex-class) (handler value))
  value)

(defn then>
  "Value-first version of `then`"
  [value handler]
  (then handler value))

(defn then-call>
  "Value-first version of `then-call`"
  [value handler]
  (then-call handler value))

(defn else>
  "Value-first version of `else`"
  [value handler]
  (else handler value))

(defn else-call>
  "Value-first version of `else-call`"
  [value handler]
  (else-call handler value))

(defn thru>
  "Value-first version of `thru`"
  [value handler]
  (thru handler value))

(defn either>
  "Value-first version of `either`"
  [value default]
  (either default value))

(defn else-if>
  "Value-first version of `else-if`"
  [value ex-class handler]
  (else-if ex-class handler value))

(defn else-call-if>
  "Value-first version of `else-call-if`"
  [value ex-class handler]
  (else-call-if ex-class handler value))

(defn thru-if>
  "Value-first version of `thru-if`"
  [value ex-class handler]
  (thru-if ex-class handler value))

(defmacro flet*
  [bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(let [result# ~(call expression)]
       (->> result#
            (then (fn [~bind-name]
                    (call (flet* ~(rest bindings) ~@body))))))
    `(do ~@body)))

(defmacro flet
  "Enables common Clojure let syntax using bindings for processing with flow"
  [bindings & body]
  `(flet* ~(partition 2 bindings) ~@body))
