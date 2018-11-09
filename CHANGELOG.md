## 1.0.0

* **BREAKING!** `fail-data`, `fail-cause` and `fail-trace` removed
* **BREAKING!** `ignored?` now accepts instance of `Throwable` instead of class
* **BREAKING!** `*exception-base-class*` is now `*catch-from*`

## 0.5.0

* Fix `fail-data` implementation
* Mark `fail-data`, `fail-cause` and `fail-trace` deprecated

## 0.4.0

* **BREAKING!** `nil` is passed to `ExceptionInfo` if no message has passed to `fail`
