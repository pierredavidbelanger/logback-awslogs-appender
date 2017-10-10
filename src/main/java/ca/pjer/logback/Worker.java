package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.amazonaws.services.logs.model.InputLogEvent;

abstract class Worker {

    private AwsLogsAppender awsLogsAppender;

    Worker(AwsLogsAppender awsLogsAppender) {
        this.awsLogsAppender = awsLogsAppender;
    }

    AwsLogsAppender getAwsLogsAppender() {
        return awsLogsAppender;
    }

    public synchronized void start() {
    }

    public synchronized void stop() {
    }

    public abstract void append(ILoggingEvent event);

    InputLogEvent asInputLogEvent(ILoggingEvent event) {
        return new InputLogEvent().withTimestamp(event.getTimeStamp())
                .withMessage(awsLogsAppender.getLayout().doLayout(event));
    }
}
