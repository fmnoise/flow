(ns dawcs.flow
  (:import [dawcs.flow Fail]))

;; vars

(def ^:dynamic *catch-from*
  "Base exception class which will be caught by `call`. Dynamic, defaults to `Throwable`. Use `catch-from!` or `catching` to modify"
  java.lang.Throwable)

(def ^:dynamic *ignored-exceptions*
  "Exception classes which will be ignored by `call`. Dynamic, defaults to empty set. Use `ignore-exceptions!`, `add-ignored-exceptions!` or `ignoring` to modify"
  #{})

;; setup

(defn ignore-exceptions!
  "Sets `*ignored-exceptions*` to given set of classes"
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
  (alter-var-root #'*catch-from* (constantly ex-class)))

;; dynamic wrappers

(defmacro catching
  "Executes body with `*exception-base-class*` bound to given class"
  {:style/indent 1}
  [exception-base-class & body]
  `(binding [*catch-from* ~exception-base-class]
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
  ([t] (fail? Throwable t))
  ([ex-class t]
   {:pre [(isa? ex-class Throwable)]}
   (isa? (class t) ex-class)))

(defn ignored?
  "Checks if exception should be ignored"
  [t]
  {:pre [(instance? Throwable t)]}
  (let [ex-class (class t)]
    (or (not (isa? ex-class *catch-from*))
        (some (partial isa? ex-class) *ignored-exceptions*))))

;; construction

(defn fail
  "Calls `ex-info` with given msg(optional, defaults to nil), data(optional, defaults to {}) and cause(optional, defaults to nil).
  Deprecated, use ex-info instead"
  {:deprecated "1.1"}
  ([] (ex-info nil {}))
  ([msg-or-data]
   (if (string? msg-or-data)
     (ex-info msg-or-data {})
     (fail nil msg-or-data)))
  ([msg data]
   {:pre [(or (nil? msg) (string? msg))]}
   (ex-info msg (if (map? data) data {::data data})))
  ([msg data cause]
   {:pre [(or (nil? msg) (string? msg)) (instance? Throwable cause)]}
   (ex-info msg (if (map? data) data {::data data}) cause)))

(defn fail!
  "Constructs `fail` with given args and throws it.
  Deprecated, use ex-info with throw instead"
  {:deprecated "1.1"}
  [& args]
  (throw (apply fail args)))

(defn fail-with
  "Constructs `Fail` with given options. Stacktrace is disabled by default"
  {:added "1.1"}
  [{:keys [msg data cause suppress? trace?] :or {data {} suppress? false trace? false} :as options}]
  {:pre [(or (nil? options) (map? options))]}
  (Fail. msg data cause suppress? trace?))

(defn fail-with!
  "Constructs `Fail` with given options and throws it. Stacktrace is enabled by default."
  {:added "1.1"}
  [{:keys [trace?] :or {trace? true} :as options}]
  (throw (fail-with (assoc options :trace? trace?))))

;; pipeline

(defn call
  "Calls given function with supplied args in `try/catch` block. When caught an exception
   which class is `*catch-from*` or a subclass of it, and is not listed in `*ignored-exceptions*`(and is not a subclass of any classes listed there)
   returns instance of caught exception, otherwise throws it. If no exception has caught during function call returns its result"
  [f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (if (ignored? t) (throw t) t))))

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
