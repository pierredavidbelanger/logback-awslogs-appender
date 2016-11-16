package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.status.WarnStatus;
import com.amazonaws.services.logs.model.InputLogEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AwsLogsAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private Layout<ILoggingEvent> layout;

    private String logGroupName;
    private String logStreamName;
    private String logRegion;
    private int maxQueueSize = 50;
    private long maxFlushTimeMillis = 0;

    private AWSLogsStub awsLogsStub;
    private Worker worker;

    @SuppressWarnings("unused")
    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    @SuppressWarnings("unused")
    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    @SuppressWarnings("unused")
    public String getLogGroupName() {
        return logGroupName;
    }

    @SuppressWarnings("unused")
    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
    }

    @SuppressWarnings("unused")
    public String getLogStreamName() {
        return logStreamName;
    }

    @SuppressWarnings("unused")
    public void setLogStreamName(String logStreamName) {
        this.logStreamName = logStreamName;
    }

    @SuppressWarnings("unused")
    public String getLogRegion() {
        return logRegion;
    }

    @SuppressWarnings("unused")
    public void setLogRegion(String logRegion) {
        this.logRegion = logRegion;
    }

    @SuppressWarnings("unused")
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @SuppressWarnings("unused")
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    @SuppressWarnings("unused")
    public long getMaxFlushTimeMillis() {
        return maxFlushTimeMillis;
    }

    @SuppressWarnings("unused")
    public void setMaxFlushTimeMillis(long maxFlushTimeMillis) {
        this.maxFlushTimeMillis = maxFlushTimeMillis;
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
                AWSLogsStub awsLogsStub = new AWSLogsStub(logGroupName, logStreamName, logRegion);
                this.awsLogsStub = awsLogsStub;
                awsLogsStub.start();
            }
            if (this.worker == null) {
                Worker worker = maxFlushTimeMillis > 0 ?
                        new AsyncWorker(awsLogsStub, getName(), maxQueueSize, maxFlushTimeMillis) :
                        new SyncWorker(awsLogsStub);
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
            worker.append(new InputLogEvent()
                    .withTimestamp(event.getTimeStamp())
                    .withMessage(layout.doLayout(event)));
        }
    }
}
