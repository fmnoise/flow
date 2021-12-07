(ns fmnoise.flow.macros)

(defmacro flet
  "Flow adaptation of Clojure `let`. Wraps evaluation of each binding to `try/catch` and handles all thrown exceptions with `Catch.caught`. If value returned from binding evaluation is failure, it's returned immediately and all other bindings and body are skipped."
  {:style/indent 1}
  [bindings & body]
  (when-not (even? (count bindings))
    (throw (ex-info "flet requires an even number of forms in binding vector" {:bindings bindings})))
  `(try
     (let ~(loop [bound []
                  tail (partition 2 bindings)]
             (if-let [[bind-name expression] (first tail)]
               (recur (into bound `[~(symbol (name bind-name)) (~'fmnoise.flow/?err
                                                                (try ~expression
                                                                     (catch :default ~(symbol "t")
                                                                       (~'fmnoise.flow/fail-with! {:data {:thrown ~(symbol "t")}})))
                                                                (fn [~'err] (~'fmnoise.flow/fail-with! {:data {:returned ~'err}})))])
                      (rest tail))
               bound))
       (try
         ~@body
         (catch :default ~'t
           (~'fmnoise.flow/fail-with! {:data {:thrown ~'t}}))))
     (catch :default ~'failure
       (let [{:keys [~'thrown ~'returned]} (ex-data ~'failure)]
         (if ~'thrown
           (fmnoise.flow/caught ~'thrown)
           ~'returned)))))
