package ca.pjer.logback;

import ca.pjer.logback.client.AwsLogsClientProperties;
import ca.pjer.logback.metrics.AwsLogsMetricsHolder;
import ca.pjer.logback.tokenisation.TokenUtility;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.status.WarnStatus;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

public class AwsLogsAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private Layout<ILoggingEvent> layout;
    private Encoder<ILoggingEvent> encoder;

    private String logGroupName;
    private String logStreamName;
    private String logStreamNamePattern;
    private String logStreamUuidPrefix;
    private String logRegion;
    private String endpoint;
    private int maxBatchLogEvents = 50;
    private long maxFlushTimeMillis = 0;
    private long maxBlockTimeMillis = 5000;
    private int retentionTimeDays = 0;
    private boolean verbose = true;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucketName;
    private String bucketPath;
    private String logFormatType;
    private String logOutputType;
    private String timezone;

    private AWSLogsStub awsLogsStub;
    private Worker worker;
    private final Map<String, Supplier<String>> logStreamNameTokenSuppliers = new HashMap<>();

    private static String startupUUID = UUID.randomUUID().toString();
    private static String startupDateTime = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());

    {
        logStreamNameTokenSuppliers.put("uuid", () -> startupUUID);
        logStreamNameTokenSuppliers.put("datetime", () -> startupDateTime);
        AwsLogsMetricsHolder.setDesired();
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogGroupName() {
        return logGroupName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogStreamName() {
        return logStreamName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogStreamName(String logStreamName) {
        this.logStreamName = logStreamName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogStreamUuidPrefix() {
        return logStreamUuidPrefix;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogStreamUuidPrefix(String logStreamUuidPrefix) {
        if (isNotBlank(logStreamUuidPrefix)) {
            this.logStreamUuidPrefix = logStreamUuidPrefix;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogStreamNamePattern() {
        return logStreamNamePattern;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogStreamNamePattern(String logStreamNamePattern) {
        this.logStreamNamePattern = logStreamNamePattern;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogRegion() {
        return logRegion;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogRegion(String logRegion) {
        this.logRegion = logRegion;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public int getMaxBatchLogEvents() {
        return maxBatchLogEvents;
    }

    // See http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
    static final int MAX_BATCH_LOG_EVENTS = 10000;

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setMaxBatchLogEvents(int maxBatchLogEvents) {
        if (maxBatchLogEvents <= 0 || maxBatchLogEvents > MAX_BATCH_LOG_EVENTS) {
            throw new IllegalArgumentException("maxBatchLogEvents must be within 1 and 10000");
        }
        this.maxBatchLogEvents = maxBatchLogEvents;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public long getMaxFlushTimeMillis() {
        return maxFlushTimeMillis;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setMaxFlushTimeMillis(long maxFlushTimeMillis) {
        this.maxFlushTimeMillis = maxFlushTimeMillis;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public long getMaxBlockTimeMillis() {
        return maxBlockTimeMillis;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setMaxBlockTimeMillis(long maxBlockTimeMillis) {
        this.maxBlockTimeMillis = maxBlockTimeMillis;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public int getRetentionTimeDays() {
        return retentionTimeDays;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setRetentionTimeDays(int days) {
        this.retentionTimeDays = days;
    }

    AWSLogsStub getAwsLogsStub() {
        return awsLogsStub;
    }

    void setAwsLogsStub(AWSLogsStub awsLogsStub) {
        this.awsLogsStub = awsLogsStub;
    }

    void setWorker(Worker worker) {
        this.worker = worker;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getCloudWatchEndpoint() {
        return endpoint;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setCloudWatchEndpoint(String endpoint) {
        if (isNotBlank(endpoint)) {
            this.endpoint = endpoint;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getEndpoint() {
        return endpoint;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setEndpoint(String endpoint) {
        if (isNotBlank(endpoint)) {
            this.endpoint = endpoint;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public boolean getVerbose() {
        return verbose;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getBucketName() {
        return bucketName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getBucketPath() {
        return bucketPath;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setBucketPath(String bucketPath) {
        this.bucketPath = bucketPath;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogFormatType() {
        return logFormatType;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogFormatType(String logFormatType) {
        this.logFormatType = logFormatType;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getLogOutputType() {
        return logOutputType;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLogOutputType(String logOutputType) {
        this.logOutputType = logOutputType;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public String getTimezone() {
        return timezone;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    @Override
    public synchronized void start() {
        if (!isStarted()) {
            if (layout == null) {
                layout = new EchoLayout<ILoggingEvent>();
                addStatus(new WarnStatus("No layout, default to " + layout, this));
            }
            if (logGroupName == null) {
                logGroupName = getClass().getSimpleName();
                addStatus(new WarnStatus("No logGroupName, default to " + logGroupName, this));
            }
            if (logStreamName == null) {
                if (logStreamNamePattern != null) {
                    logStreamName = TokenUtility.replaceTokens(logStreamNamePattern, logStreamNameTokenSuppliers);
                } else if (logStreamUuidPrefix != null) {
                    logStreamName = String.format("%s%s", logStreamUuidPrefix, startupUUID);
                } else {
                    logStreamName = startupDateTime;
                    addStatus(new WarnStatus("No logStreamName, default to " + logStreamName, this));
                }
            }

            ZoneId zoneId = (timezone == null) ? ZoneId.systemDefault() : ZoneId.of(timezone);

            if (this.awsLogsStub == null) {
                this.awsLogsStub = new AWSLogsStub(new AwsLogsClientProperties(logGroupName, logStreamName, logRegion, retentionTimeDays, endpoint, verbose, accessKeyId, secretAccessKey, bucketName, bucketPath, logFormatType, logOutputType, zoneId));
                this.awsLogsStub.start();
            }
            if (this.worker == null) {
                Worker worker = maxFlushTimeMillis > 0 ?
                        new AsyncWorker(this) :
                        new SyncWorker(this);
                this.worker = worker;
                worker.start();
            }
            layout.start();
            super.start();
        }
    }

    @Override
    public synchronized void stop() {
        if (isStarted()) {
            super.stop();
            layout.stop();
            if (worker != null) {
                worker.stop();
                worker = null;
            }
            if (awsLogsStub != null) {
                awsLogsStub.stop();
                awsLogsStub = null;
            }
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (worker != null) {
            worker.append(event);
        }
    }

    public String encode(ILoggingEvent loggingEvent) {
        if (encoder != null) {
            return bytesUtf8ToString(encoder.encode(loggingEvent));
        }

        return layout.doLayout(loggingEvent);
    }

    private String bytesUtf8ToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isNotBlank(String text) {
        return Objects.nonNull(text) && !text.trim().isEmpty();
    }
}
