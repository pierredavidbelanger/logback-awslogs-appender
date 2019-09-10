package ca.pjer.logback;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;

import java.util.*;

class AWSLogsStub {
    private final Comparator<InputLogEvent> inputLogEventByTimestampComparator = Comparator.comparing(InputLogEvent::getTimestamp);
    private final String logGroupName;
    private final String logStreamName;
    private final String logRegion;
    private String sequenceToken;
    private Long lastTimestamp;
    private int retentionTimeInDays;

    private final Lazy<AWSLogs> lazyAwsLogs = new Lazy<>();

    AWSLogsStub(String logGroupName, String logStreamName, String logRegion, int retentionTimeInDays) {
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;
        this.logRegion = logRegion;
        this.retentionTimeInDays = retentionTimeInDays;
    }

    private AWSLogs awsLogs() {
        return lazyAwsLogs.getOrCompute(() -> {
            AWSLogsClientBuilder builder = AWSLogsClientBuilder.standard();
            Optional.ofNullable(logRegion).ifPresent(builder::setRegion);

            AWSLogs awsLogs = builder.build();
            initLogGroup(awsLogs);
            return awsLogs;
        });
    }

    private void initLogGroup(AWSLogs awsLogs) {
        try {
            awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(logGroupName));
            if(retentionTimeInDays > 0) {
                awsLogs.putRetentionPolicy(new PutRetentionPolicyRequest()
                        .withLogGroupName(logGroupName)
                        .withRetentionInDays(retentionTimeInDays));
            }
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        }
        try {
            awsLogs.createLogStream(new CreateLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        }
    }

    synchronized void start() {
    }

    synchronized void stop() {
        try {
            awsLogs().shutdown();
        } catch (Exception e) {
            // ignore
        }
    }

    synchronized void logEvents(Collection<InputLogEvent> events) {
        if (events.size() > 1) {
            List<InputLogEvent> sortedEvents = new ArrayList<InputLogEvent>(events);
            Collections.sort(sortedEvents, inputLogEventByTimestampComparator);
            events = sortedEvents;
        }
        for (InputLogEvent event : events) {
            if (lastTimestamp != null && event.getTimestamp() < lastTimestamp) {
                event.setTimestamp(lastTimestamp);
            } else {
                lastTimestamp = event.getTimestamp();
            }
        }
        logPreparedEvents(events);
    }

    private void logPreparedEvents(Collection<InputLogEvent> events) {
        try {
            PutLogEventsRequest request = new PutLogEventsRequest()
                    .withLogGroupName(logGroupName)
                    .withLogStreamName(logStreamName)
                    .withSequenceToken(sequenceToken)
                    .withLogEvents(events);
            PutLogEventsResult result = awsLogs().putLogEvents(request);
            sequenceToken = result.getNextSequenceToken();
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken = e.getExpectedSequenceToken();
        } catch (InvalidSequenceTokenException e) {
            sequenceToken = e.getExpectedSequenceToken();
            logPreparedEvents(events);
        }
    }
}
