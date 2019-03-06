## 1.1.0

* Added custom container for failure representation with ability to skip stacktrace
* Added `fail-with` and `fail-with!` - map-oriented Fail construction helpers
* Mark `fail` and `fail!` deprecated

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
