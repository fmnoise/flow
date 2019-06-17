(ns dawcs.flow
  (:import [dawcs.flow Fail]))

(defprotocol ErrorHandling
  "Defines behavior for handling each class of exceptions"
  (handle [t]))

(extend-protocol ErrorHandling
  java.lang.Throwable
  (handle [t] t))

(defn fail?
  "Checks if value is exception of given class(optional, defaults to `Throwable`)"
  ([t] (fail? Throwable t))
  ([ex-class t]
   {:pre [(isa? ex-class Throwable)]}
   (isa? (class t) ex-class)))

(defn fail-with
  "Constructs `Fail` with given options. Stacktrace is disabled by default"
  {:added "2.0"}
  [{:keys [msg data cause suppress? trace?] :or {data {} suppress? false trace? false} :as options}]
  {:pre [(or (nil? options) (map? options))]}
  (Fail. msg data cause suppress? trace?))

(defn fail-with!
  "Constructs `Fail` with given options and throws it. Stacktrace is enabled by default."
  {:added "2.0"}
  [{:keys [trace?] :or {trace? true} :as options}]
  (throw (fail-with (assoc options :trace? trace?))))

(defn call
  "Calls given function with supplied args in `try/catch` block, then calls `ErrorHandling/handle` on caught exception. If no exception has caught during function call returns its result"
  [f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (handle t))))

(defn call-with
  "Calls given function with supplied args in `try/catch` block, then calls handler on caught exception. If no exception has caught during function call returns its result"
  {:added "2.0"}
  [handler f & args]
  (try (apply f args)
    (catch java.lang.Throwable t
      (handler t))))

(defn then
  "If value is not a `fail?`, applies f to it, otherwise returns value"
  [f value]
  (if (fail? value) value (f value)))

(defn then-call
  "If value is not a `fail?`, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  [f value]
  (if (fail? value) value (call f value)))

(defn else
  "If value is a `fail?`, calls applies f to it, otherwise returns value"
  [f value]
  (if (fail? value) (f value) value))

(defn else-call
  "If value is a `fail?`, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  [f value]
  (if (fail? value) (call f value) value))

(defn thru
  "Applies f to value (for side effects). Returns value. Works similar to `doto`, but accepts function as first arg"
  [f value]
  (f value)
  value)

(defn thru-call
  "Applies f to value wrapped to `call` (for side effects). Returns value. Works similar to `doto`, but accepts function as first arg. Please not that exception thrown inside of function will be silently ignored by default"
  [f value]
  (call f value)
  value)

(defn else-if
  "If value is an exception of ex-class, applies f to it, otherwise returns value"
  [ex-class f value]
  {:pre [(isa? ex-class Throwable)]}
  (if (isa? (class value) ex-class) (f value) value))

(defmacro ^:no-doc flet*
  [handler bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(->> (call-with ~handler (fn [] ~expression))
          (then (fn [~bind-name]
                  (flet* ~handler ~(rest bindings) ~@body))))
    `(call-with ~handler (fn [] ~@body))))

(defmacro flet
  "Flow adaptation of Clojure `let`. Wraps evaluation of each binding to `call-with` with default handler (defined with `ErrorsHandling/handle`). If value returned from binding evaluation is `fail?`, it's returned immediately and all other bindings and body are skipped. Custom exception handler function may be passed as first binding with name `:handler`"
  {:style/indent 1}
  [bindings & body]
  (let [handler-given? (= (first bindings) :handler)
        handler (if handler-given? (second bindings) handle)
        bindings (if handler-given? (rest (rest bindings)) bindings)]
    `(flet* ~handler ~(partition 2 bindings) ~@body)))
