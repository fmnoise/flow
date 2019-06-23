(ns dawcs.flow
  #?(:clj (:import [dawcs.flow Fail])))

(defprotocol Flow
  (on-success [this f] "if value is not a failure, apply f to it")
  (on-failure [this f] "if value is a failure, apply f to it"))

#?(:clj
   (extend-protocol Flow
     java.lang.Object
     (on-success [this f] (f this))
     (on-failure [this f] this)

     nil
     (on-success [this f] (f this))
     (on-failure [this f] this)

     java.lang.Throwable
     (on-success [this f] this)
     (on-failure [this f] (f this)))

   :cljs
   (extend-protocol Flow
     js/Object
     (on-success [this f] (f this))
     (on-failure [this f] this)

     nil
     (on-success [this f] (f this))
     (on-failure [this f] this)

     js/Error
     (on-success [this f] this)
     (on-failure [this f] (f this))))

(defprotocol Catch
  (caught [t] "defines how to process caught exception"))

#?(:clj
   (extend-protocol Catch
     java.lang.Throwable
     (caught [t] t))

   :cljs
   (extend-protocol Catch
     js/Error
     (caught [t] t)))

#?(:clj
   (defn fail-with
     "Constructs `Fail` with given options. Stacktrace is disabled by default"
     {:added "2.0"}
     [{:keys [msg data cause suppress? trace?] :or {data {} suppress? false trace? false} :as options}]
     {:pre [(or (nil? options) (map? options))]}
     (Fail. msg data cause suppress? trace?)))

#?(:clj
   (defn fail-with!
     "Constructs `Fail` with given options and throws it. Stacktrace is enabled by default."
     {:added "2.0"}
     [{:keys [trace?] :or {trace? true} :as options}]
     (throw (fail-with (assoc options :trace? trace?)))))

(defn fail?
  "Checks if given value is considered as failure"
  [t]
  #?(:clj
     (or (instance? java.lang.Throwable t)
         (instance? Fail (on-failure t (constantly (fail-with {})))))
     :cljs
     (or (instance? js/Error t)
         (instance? js/Error (on-failure t (constantly (js/Error.)))))))

(defn chain
  "Passes given value through chain of functions. If value is failure or any function in chain returns failure, it's returned and rest of chain is skipped"
  [v f & fs]
  (loop [res (on-success v f)
         chain fs]
    (if (seq chain)
      (recur (on-success res (first chain))
             (rest chain))
      res)))

(defn call
  "Calls given function with supplied args in `try/catch` block, then calls `Catch.caught` on caught exception. If no exception has caught during function call returns its result"
  [f & args]
  (try
    (apply f args)
    (catch #?(:clj java.lang.Throwable :cljs :default) t
      (caught t))))

(defn call-with
  "Calls given function with supplied args in `try/catch` block, then calls catch-handler on caught exception. If no exception has caught during function call returns its result"
  {:added "2.0"}
  [catch-handler f & args]
  (try
    (apply f args)
    (catch #?(:clj java.lang.Throwable :cljs :default) t
      (catch-handler t))))

;; functor

(defn then
  "If value is not a failure, applies f to it, otherwise returns value"
  [f value]
  (on-success value f))

(defn then-call
  "If value is not a failure, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  [f value]
  (on-success value (partial call f)))

(defn else
  "If value is a failure, calls applies f to it, otherwise returns value"
  [f value]
  (on-failure value f))

(defn else-call
  "If value is a failure, applies f to it wrapped to `call`, otherwise returns value"
  {:added "2.0"}
  [f value]
  (on-failure value (partial call f)))

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

#?(:clj
   (defn else-if
     "If value is an exception of ex-class, applies f to it, otherwise returns value"
     [ex-class f value]
     {:pre [(isa? ex-class Throwable)]}
     (if (isa? (class value) ex-class) (f value) value)))

;; flet

(defmacro ^:no-doc flet*
  [catch-handler bindings & body]
  (if-let [[bind-name expression] (first bindings)]
    `(on-success
      (call-with ~catch-handler (fn [] ~expression))
      (fn [~bind-name] (flet* ~catch-handler ~(rest bindings) ~@body)))
    `(call-with ~catch-handler (fn [] ~@body))))

(defmacro flet
  "Flow adaptation of Clojure `let`. Wraps evaluation of each binding to `call-with` with default handler (defined with `Catch.caught`). If value returned from binding evaluation is failure, it's returned immediately and all other bindings and body are skipped. Custom exception handler function may be passed as first binding with name `:caught`"
  {:style/indent 1}
  [bindings & body]
  (let [handler-given? (= (first bindings) :caught)
        catch-handler (if handler-given? (second bindings) #(caught %))
        bindings (if handler-given? (rest (rest bindings)) bindings)]
    `(flet* ~catch-handler ~(partition 2 bindings) ~@body)))

;; legacy

#?(:clj
   (defn fail
     "Calls `ex-info` with given msg(optional, defaults to nil), data(optional, defaults to {}) and cause(optional, defaults to nil). Deprecated, use `ex-info` instead"
     {:deprecated "2.0"}
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
      (ex-info msg (if (map? data) data {::data data}) cause))))

#?(:clj
   (defn fail!
     "Constructs `fail` with given args and throws it. Deprecated, use `ex-info` with `throw` instead"
     {:deprecated "2.0"}
     [& args]
     (throw (apply fail args))))
