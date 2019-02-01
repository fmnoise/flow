package dawcs.flow;

import clojure.lang.IExceptionInfo;
import clojure.lang.IPersistentMap;

public class Fail extends RuntimeException implements IExceptionInfo {
    public final IPersistentMap data;

    public Fail(String s, IPersistentMap data) {
        this(s, data, null);
    }

    public Fail(String s, IPersistentMap data, Throwable throwable) {
        super(s, throwable);
        if (data != null) {
            this.data = data;
        }  else {
            throw new IllegalArgumentException("Additional data must be non-nil.");
        }
    }

    public Fail(String s, IPersistentMap data, Throwable throwable, boolean enableSuppression, boolean writableStackTrace) {
        super(s, throwable, enableSuppression, writableStackTrace);
        if (data != null) {
            this.data = data;
        }  else {
            throw new IllegalArgumentException("Additional data must be non-nil.");
        }
    }

    public IPersistentMap getData() {
        return data;
    }

    public String toString() {
        return "Fail: " + getMessage() + " " + data.toString();
    }
}