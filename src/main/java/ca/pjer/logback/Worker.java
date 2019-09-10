package ca.pjer.logback;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import com.amazonaws.services.logs.model.InputLogEvent;

abstract class Worker<E> {

    private AwsLogsAppender<E> awsLogsAppender;

    Worker(AwsLogsAppender<E> awsLogsAppender) {
        this.awsLogsAppender = awsLogsAppender;
    }

    AwsLogsAppender getAwsLogsAppender() {
        return awsLogsAppender;
    }

    public synchronized void start() {
    }

    public synchronized void stop() {
    }

    public abstract void append(E event);

    // See https://github.com/pierredavidbelanger/logback-awslogs-appender/issues/6
    private static final int MAX_EVENT_SIZE = 262144;

    InputLogEvent asInputLogEvent(E event) {
        long timestamp;
        try {
            // get timestamp by reflection to avoid dependency to logback-access-library
            timestamp = (long) event.getClass().getMethod("getTimeStamp").invoke(event);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
            timestamp = System.currentTimeMillis();
        }

        InputLogEvent inputLogEvent = new InputLogEvent().withTimestamp(timestamp)
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
