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

;; construction

(defn fail?
  "Checks if value is Throwable"
  [value]
  (isa? (class value) java.lang.Throwable))

(defn fail
  "Creates new `ex-info` instance with given msg, data(optional) and cause(optional)"
  ([msg] (fail msg {}))
  ([msg data] (ex-info msg (if (map? data) data {::data data})))
  ([msg data cause] (ex-info msg data cause)))

(defn fail!
  "Creates new `ex-info` instance with given msg, data(optional) and cause(optional) and throws it"
  ([msg] (fail! msg {}))
  ([msg data] (throw (ex-info msg (if (map? data) data {::context data}))))
  ([msg data cause] (throw (ex-info msg data cause))))

;; pipeline

(defn- catchable? [ex]
  (let [ex-class (class ex)]
    (and (isa? ex-class *exception-base-class*)
         (not (some (partial isa? ex-class) *ignored-exceptions*)))))

(defn call
  "Wraps given function call with supplied args in `try/catch` block. When caught an exception
   which class is `*exception-base-class*`(defaults to `Throwable`) or a subclass of it,
   returns it, otherwise `throw`s it. Returns function call result if no exception has caught"
  [f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (if (catchable? t) t (throw t)))))

(defn then
  "If value is not a `fail?`, applies f to it wrapped to `call`, otherwise returns value"
  [handler value]
  (if (fail? value) value (call handler value)))

(defn else
  "If value is a `fail?`, calls handler on it, otherwise returns value"
  [handler value]
  (if (fail? value) (handler value) value))

(defn thru
  "If value is an `fail?`, calls handler on it (for side effects). Returns value"
  [handler value]
  (when (fail? value) (handler value))
  value)

;; flet

(defmacro ^:no-doc flet*
  [bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(->> (call (fn [] ~expression))
          (then (fn [~bind-name]
                  (flet* ~(rest bindings) ~@body))))
    `(do ~@body)))

(defmacro flet
  "Enables common Clojure let syntax using bindings for processing with flow"
  {:style/indent 1}
  [bindings & body]
  `(flet* ~(partition 2 bindings) ~@body))
