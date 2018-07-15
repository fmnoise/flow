(ns dawcs.flow)

(def ^:dynamic *exception-base-class*
  "Base exception class which will be caught by `call`. Dynamic, defaults to `Throwable`"
  java.lang.Throwable)

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
  "Executes body in `try` block. When caught an exception
  which class is `*exception-base-class*`(defaults to `Throwable`) or a subclass of it,
  returns it, otherwise `throw`s it. Returns value of body if no exception has caught"
  [& body]
  `(try ~@body
     (catch java.lang.Throwable t#
       (if (isa? (class t#) *exception-base-class*)
         t#
         (throw t#)))))

(defmacro catching
  "Executes body with `*exception-base-class*` bound to given class"
  [exception-base-class & body]
  `(binding [*exception-base-class* ~exception-base-class]
     ~@body))

(defn raise
  "If value is an exception, throws it, otherwise returns value"
  [value]
  (if (fail? value) (throw value) value))

(defn then
  "If value is not an exception, applies f to it wrapped in `call`, otherwise returns value"
  [handler value]
  (if (fail? value) value (call (handler value))))

(defn else
  "If value is an exception of ex-class(optional), applies handler to it wrapped in `call`, otherwise returns value"
  ([handler value]
   (if (fail? value) (call (handler value)) value))
  ([ex-class handler value]
   (if-not (isa? ex-class java.lang.Throwable)
     (throw (java.lang.IllegalArgumentException. "ex-class argument should be a proper Exception class"))
     (if (isa? (class value) ex-class) (call (handler value)) value))))

(defn either
  "If value is an exception, returns default, otherwise returns value"
  [default value]
  (if (fail? value) default value))

(defn thru
  "If value is an exception of ex-class(optional), calls handler on it (for side effects). Returns value"
  ([handler value]
   (when (fail? value) (handler value))
   value)
  ([ex-class handler value]
   (if-not (isa? ex-class java.lang.Throwable)
     (throw (java.lang.IllegalArgumentException. "ex-class argument should be a proper Exception class"))
     (do
       (when (isa? (class value) ex-class) (handler value))
       value))))

(defn then>
  "Value-first version of `then`"
  [value handler]
  (then handler value))

(defn else>
  "Value-first version of `else`"
  ([value handler]
   (else handler value))
  ([value ex-class handler]
   (else ex-class handler value)))

(defn either>
  "Value-first version of `either`"
  [value default]
  (either default value))

(defn thru>
  "Value-first version of `thru`"
  ([value handler]
   (thru handler value))
  ([value ex-class handler]
   (thru ex-class handler value)))

(defmacro flet*
  [bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(let [result# ~(call expression)]
       (->> result#
            (then (fn [~bind-name]
                    (flet* ~(rest bindings) ~@body)))))
    `(do ~@body)))

(defmacro flet
  "Enables common Clojure let syntax using bindings for processing with flow"
  [bindings & body]
  `(flet* ~(partition 2 bindings) ~@body))
