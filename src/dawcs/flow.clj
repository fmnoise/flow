(ns dawcs.flow)

;; vars

(def ^:dynamic *exception-base-class*
  "Base exception class which will be caught by `call`. Dynamic, defaults to `Throwable`"
  java.lang.Throwable)

(def ^:dynamic *ignored-exceptions*
  "Exception classes which will be ignored by `call`. Dynamic, defaults to empty set"
  #{})

;; setup

(defn ignore-exceptions!
  "Sets `*ignored-exceptions*` to given set"
  [ex-class-set]
  {:pre [(set? ex-class-set)]}
  (alter-var-root #'*ignored-exceptions* (constantly ex-class-set)))

(defn add-ignored-exceptions!
  "Adds given set of classes to `*ignored-exceptions*`"
  [ex-class-set]
  {:pre [(set? ex-class-set)]}
  (alter-var-root #'*ignored-exceptions* into ex-class-set))

(defn catch-from!
  "Sets *exception-base-class* to specified class"
  [ex-class]
  {:pre [(isa? ex-class Throwable)]}
  (alter-var-root #'*exception-base-class* (constantly ex-class)))

;; dynamic wrappers

(defmacro catching
  "Executes body with `*exception-base-class*` bound to given class"
  {:style/indent 1}
  [exception-base-class & body]
  `(binding [*exception-base-class* ~exception-base-class]
     ~@body))

(defmacro ignoring
  "Executes body with `*ignored-exceptions*` bound to given value"
  {:style/indent 1}
  [ignored-exceptions & body]
  `(binding [*ignored-exceptions* ~ignored-exceptions]
     ~@body))

;; checking

(defn fail?
  "Checks if value is exception of given class(optional, defaults to Throwable)"
  ([value] (fail? Throwable value))
  ([ex-class value]
   {:pre [(isa? ex-class Throwable)]}
   (isa? (class value) ex-class)))

(defn ignored?
  "Checks if exception class should be ignored"
  [ex-class]
  (some (partial isa? ex-class) *ignored-exceptions*))

(defn fail
  "Creates new `ex-info` instance with given msg, data(optional) and cause(optional)"
  ([msg-or-data] (if (string? msg-or-data)
                   (fail msg-or-data {})
                   (fail "" msg-or-data)))
  ([msg data] (ex-info msg (if (map? data) data {::data data})))
  ([msg data cause] (ex-info msg data cause)))

(defn fail-cause
  "Returns fail cause"
  [fail]
  (-> fail Throwable->map :cause))

(defn fail-data
  "Returns fail data"
  [fail]
  (-> fail Throwable->map :data))

(defn fail-trace
  "Returns fail trace"
  [fail]
  (-> fail Throwable->map :trace))

;; pipeline

(defn call
  "Calls given function with supplied args in `try/catch` block. When caught an exception
   which class is `*exception-base-class*` or a subclass of it, and is not listed in `*ignored-exceptions*`(and is not a subclass of any classes listed there)
   returns instance of caught exception, otherwise throws it. If no exception has caught during function call returns its result"
  [f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (let [ex-class (class t)]
        (if (and (isa? ex-class *exception-base-class*)
                 (not (ignored? ex-class)))
          t
          (throw t))))))

(defn then
  "If value is not a `fail?`, applies handler to it wrapped to `call`, otherwise returns value"
  [handler value]
  (if (fail? value) value (call handler value)))

(defn else
  "If value is a `fail?`, calls handler on it, otherwise returns value"
  [handler value]
  (if (fail? value) (handler value) value))

(defn thru
  "Calls handler on value (for side effects). Returns value. Works similar to doto, but accepts function as first arg"
  [handler value]
  (handler value)
  value)

(defn else-if
  "If value is an exception of ex-class, applies handler to it, otherwise returns value"
  [ex-class handler value]
  {:pre [(isa? ex-class Throwable)]}
  (if (isa? (class value) ex-class) (handler value) value))

;; flet

(defmacro ^:no-doc flet*
  [bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(->> (call (fn [] ~expression))
          (then (fn [~bind-name]
                  (flet* ~(rest bindings) ~@body))))
    `(do ~@body)))

(defmacro flet
  "Flow adaptation of Clojure `let`. Wraps evaluation of each binding to `call`. If `fail?` value returned from binding evaluation, it's returned immediately and all other bindings and body are skipped"
  {:style/indent 1}
  [bindings & body]
  `(flet* ~(partition 2 bindings) ~@body))
