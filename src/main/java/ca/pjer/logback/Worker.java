package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.Charset;

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

    // See https://github.com/pierredavidbelanger/logback-awslogs-appender/issues/6
    private static final int MAX_EVENT_SIZE = 262144;

    InputLogEvent asInputLogEvent(ILoggingEvent event) {
        InputLogEvent inputLogEvent = new InputLogEvent().withTimestamp(event.getTimeStamp())
                .withMessage(awsLogsAppender.getLayout().doLayout(event));

        if (eventSize(inputLogEvent) > MAX_EVENT_SIZE) {
            awsLogsAppender
                    .addWarn(String.format("Log message exceeded Cloudwatch Log's limit of %d bytes", MAX_EVENT_SIZE));
            trimMessage(inputLogEvent, MAX_EVENT_SIZE);
        }

        return inputLogEvent;
    }

    // See http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
    private static final int EVENT_SIZE_PADDING = 26;
    private static final Charset EVENT_SIZE_CHARSET = Charset.forName("UTF-8");

    static final int eventSize(InputLogEvent event) {
        return event.getMessage().getBytes(EVENT_SIZE_CHARSET).length + EVENT_SIZE_PADDING;
    }

    private static final String ELLIPSIS = "...";

    private static final void trimMessage(InputLogEvent event, int eventSize) {
        int trimmedMessageSize = eventSize - EVENT_SIZE_PADDING - ELLIPSIS.getBytes(EVENT_SIZE_CHARSET).length;
        byte[] message = event.getMessage().getBytes(EVENT_SIZE_CHARSET);

        String unsafeTrimmed = new String(message, 0, trimmedMessageSize + 1, EVENT_SIZE_CHARSET);
        // The last character might be a chopped UTF-8 character
        String trimmed = unsafeTrimmed.substring(0, unsafeTrimmed.length() - 1);

        event.setMessage(trimmed + ELLIPSIS);
    }
}
