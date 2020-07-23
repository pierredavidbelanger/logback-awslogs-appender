package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.status.WarnStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class AwsLogsAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private Layout<ILoggingEvent> layout;

    private String logGroupName;
    private String logStreamName;
    private String logRegion;
    private String cloudWatchEndpoint;
    private int maxBatchLogEvents = 50;
    private long maxFlushTimeMillis = 0;
    private long maxBlockTimeMillis = 5000;
    private int retentionTimeDays = 0;
    private boolean verbose = true;

    private AWSLogsStub awsLogsStub;
    private Worker worker;

    private static volatile boolean created;

    {
        created = true;
    }

    public static boolean isCreated() {
        return created;
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
    public int getRetentionTimeDays() { return retentionTimeDays; }

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
        return cloudWatchEndpoint;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setCloudWatchEndpoint(String cloudWatchEndpoint) {
        if (Objects.nonNull(cloudWatchEndpoint) && !cloudWatchEndpoint.trim().isEmpty()) {
            this.cloudWatchEndpoint = cloudWatchEndpoint;
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
                logStreamName = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());
                addStatus(new WarnStatus("No logStreamName, default to " + logStreamName, this));
            }
            if (this.awsLogsStub == null) {
                this.awsLogsStub = new AWSLogsStub(logGroupName, logStreamName, logRegion, retentionTimeDays, cloudWatchEndpoint, verbose);
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
}
