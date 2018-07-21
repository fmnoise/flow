(ns dawcs.flow)

;; vars

(def ^:dynamic *exception-base-class*
  "Base exception class which will be caught by `call`. Dynamic, defaults to `Throwable`"
  java.lang.Throwable)

(def ^:dynamic *ignored-exceptions*
  "Exception classes which will be ignored by `call`. Dynamic, defaults to empty set"
  #{})

;; impl

(defn- ex-class-arg-check [ex-class]
  (when-not (isa? ex-class *exception-base-class*)
    (throw (IllegalArgumentException. (str "ex-class argument should be a subclass of " *exception-base-class* " but got " ex-class " instead")))))

(defn- catchable? [ex]
  (let [ex-class (class ex)]
    (and (isa? ex-class *exception-base-class*)
         (not (some (partial isa? ex-class) *ignored-exceptions*)))))

;; setup

(defn ignore-exceptions!
  "Sets `*ignored-exceptions*` to given set via `alter-var-root`"
  [ex-class-set]
  {:pre [(set? ex-class-set)]}
  (alter-var-root #'*ignored-exceptions* (constantly ex-class-set)))

(defn add-ignored-exceptions!
  "Adds given set of classes to `*ignored-exceptions*` via `alter-var-root`"
  [ex-class-set]
  {:pre [(set? ex-class-set)]}
  (alter-var-root #'*ignored-exceptions* into ex-class-set))

(defn catch-from!
  "Sets *exception-base-class* to given ex-class via `alter-var-root`"
  [ex-class]
  {:pre [(isa? ex-class Throwable)]}
  (alter-var-root #'*exception-base-class* (constantly ex-class)))

;; dynamic wrappers

(defmacro catching
  "Executes body with `*exception-base-class*` bound to given class"
  [exception-base-class & body]
  `(binding [*exception-base-class* ~exception-base-class]
     ~@body))

(defmacro ignoring
  "Executes body with `*ignored-exceptions*` bound to given value"
  [ignored-exceptions & body]
  `(binding [*ignored-exceptions* ~ignored-exceptions]
     ~@body))

;; construction

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

;; base pipeline

(defn call
  "Wraps given function call with supplied args in `try/catch` block. When caught an exception
   which class is `*exception-base-class*`(defaults to `Throwable`) or a subclass of it,
   returns it, otherwise `throw`s it. Returns function call result if no exception has caught"
  [f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (if (catchable? t) t (throw t)))))

(defn raise
  "If value is a `fail?`, throws it, otherwise returns value"
  [value]
  (if (fail? value) (throw value) value))

(defn then
  "If value is not a `fail?`, applies f to it wrapped to `call`, otherwise returns value"
  [handler value]
  (if (fail? value) value (call handler value)))

(defn else
  "If value is a `fail?`, applies handler to it wrapped to `call`, otherwise returns value"
  [handler value]
  (if (fail? value) (call handler value) value))

(defn thru
  "If value is an `fail?`, calls handler on it (for side effects). Returns value"
  [handler value]
  (when (fail? value) (handler value))
  value)

(defn either
  "If value is a `fail?`, returns default, otherwise returns value"
  [default value]
  (if (fail? value) default value))

;; conditional pipeline

(defn else-if
  "If value is an exception of ex-class, applies handler to it, otherwise returns value"
  [ex-class handler value]
  (ex-class-arg-check ex-class)
  (if (isa? (class value) ex-class) (call handler value) value))

(defn thru-if
  "If value is an exception of ex-class, calls handler on it (for side effects). Returns value"
  [ex-class handler value]
  (ex-class-arg-check ex-class)
  (when (isa? (class value) ex-class) (handler value))
  value)

;; thread-first

(defn then>
  "Value-first version of `then`"
  [value handler]
  (then handler value))

(defn else>
  "Value-first version of `else`"
  [value handler]
  (else handler value))

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

(defn thru-if>
  "Value-first version of `thru-if`"
  [value ex-class handler]
  (thru-if ex-class handler value))

;; flet

(defmacro flet*
  [bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(->> (call (fn [] ~expression))
          (then (fn [~bind-name]
                  (flet* ~(rest bindings) ~@body))))
    `(do ~@body)))

(defmacro flet
  "Enables common Clojure let syntax using bindings for processing with flow"
  [bindings & body]
  `(flet* ~(partition 2 bindings) ~@body))