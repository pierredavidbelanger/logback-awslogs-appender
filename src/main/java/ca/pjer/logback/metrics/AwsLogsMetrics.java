package ca.pjer.logback.metrics;

public interface AwsLogsMetrics {
    void incrementLostCount();
    void incrementBatchRequeueFailed();
    void incrementFlushFailed(Throwable exception);
    void incrementPutLogFailed(Throwable exception);
    void incrementBatch(int batchSize);
    void incrementLogEvents(int eventCount);
    void incrementPutLog();
}
