package ca.pjer.logback.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AwsLogsMetricsHolder {
    private static AwsLogsMetrics NULL = new AwsLogsMetrics() {
        @Override
        public void incrementLostCount() {

        }

        @Override
        public void incrementBatchRequeueFailed() {

        }

        @Override
        public void incrementFlushFailed(Throwable exception) {

        }

        @Override
        public void incrementPutLogFailed(Throwable exception) {

        }

        @Override
        public void incrementBatch(int batchSize) {

        }

        @Override
        public void incrementLogEvents(int eventCount) {

        }

        @Override
        public void incrementPutLog() {

        }
    };

    public static AtomicReference<AwsLogsMetrics> INSTANCE = new AtomicReference<>(NULL);
    public static AtomicBoolean DESIRED = new AtomicBoolean();


    public static AwsLogsMetrics get() {
        return INSTANCE.get();
    }

    @SuppressWarnings("unused")
    public static void set(AwsLogsMetrics metrics) {
        INSTANCE.set(metrics);
    }

    public static void setDesired() {
        DESIRED.set(true);
    }

    @SuppressWarnings("unused")
    public static boolean isDesired() {
        return DESIRED.get();
    }
}
