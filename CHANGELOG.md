## 3.0.0

* **BREAKING!** new protocol-based implementation for setting up errors handling (default behavior is still the same though)
* **BREAKING!** removed all vars, functions and macros for setting up errors handling: `*catch-from*`, `*ignored-exceptions*`, `*default-handler*`, `catching`, `ignoring`, `catch-from!`, `ignore-exceptions!`, `add-ignored-exceptions!`, `ignored?`
* **BREAKING!** removed deprecated `ex-info` construction helpers `fail` and `fail!`

## 2.0.0

* **BREAKING!** `then` doesn't wrap to `call` anymore, use `then-call` to achieve that
* Added `call`-wrapping `then-call`, `else-call` and `thru-call`
* Added `Fail` - custom container for failure representation with ability to skip stacktrace
* Added `fail-with` and `fail-with!` - map-oriented `Fail` construction helpers
* Added `*default-handler*` and `call-with` for more functional and thread-safe exceptions handling
* Added ability to pass exceptions handler to `flet`
* Mark `fail`, `fail!`, `catching` and `ignoring` deprecated

## 1.0.0

* Added `fail!` - fail throwing shortcut
* **BREAKING!** Removed `fail-data`, `fail-cause` and `fail-trace`
* **BREAKING!** `ignored?` now accepts instance of `Throwable` instead of class
* **BREAKING!** `*exception-base-class*` is now `*catch-from*`

## 0.5.0

* Fix `fail-data` implementation
* Mark `fail-data`, `fail-cause` and `fail-trace` deprecated

## 0.4.0

* **BREAKING!** `nil` is passed to `ExceptionInfo` if no message has passed to `fail`
