package ca.pjer.logback;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

class Lazy<T> {
    private volatile T value;

    public T getOrCompute(Supplier<T> supplier) {
        final T result = value;  // Read volatile just once...
        return result == null ? maybeCompute(supplier) : result;
    }

    public T get() {
        return value;
    }

    private synchronized T maybeCompute(Supplier<T> supplier) {
        if (value == null) {
            value = requireNonNull(supplier.get());
        }
        return value;
    }
}