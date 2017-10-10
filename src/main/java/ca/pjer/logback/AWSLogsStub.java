package ca.pjer.logback;

import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import java.util.*;

class AWSLogsStub {

    private final Comparator<InputLogEvent> inputLogEventByTimestampComparator = new Comparator<InputLogEvent>() {

        @Override
        public int compare(InputLogEvent o1, InputLogEvent o2) {
            return o1.getTimestamp().compareTo(o2.getTimestamp());
        }
    };

    private final String logGroupName;
    private final String logStreamName;
    private final AWSLogs awsLogs;

    private String sequenceToken;
    private Long lastTimestamp;

    AWSLogsStub(String logGroupName, String logStreamName, String logRegion) {

        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;

        AWSLogs awsLogs = new AWSLogsClient();
        if (logRegion != null) {
            awsLogs.setRegion(RegionUtils.getRegion(logRegion));
        }
        this.awsLogs = awsLogs;
    }

    synchronized void start() {
        try {
            awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(logGroupName));
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        }
        try {
            awsLogs.createLogStream(new CreateLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        }
    }

    synchronized void stop() {
        try {
            awsLogs.shutdown();
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
            PutLogEventsResult result = awsLogs.putLogEvents(request);
            sequenceToken = result.getNextSequenceToken();
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken = e.getExpectedSequenceToken();
        } catch (InvalidSequenceTokenException e) {
            sequenceToken = e.getExpectedSequenceToken();
            logPreparedEvents(events);
        }
    }
}
