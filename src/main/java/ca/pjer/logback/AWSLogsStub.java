package ca.pjer.logback;

import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import java.util.Collection;

class AWSLogsStub {

    private final String logGroupName;
    private final String logStreamName;
    private final AWSLogs awsLogs;

    private String sequenceToken;

    public AWSLogsStub(String logGroupName, String logStreamName, String logRegion) {

        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;

        AWSLogs awsLogs = new AWSLogsClient();
        if (logRegion != null) {
            awsLogs.setRegion(RegionUtils.getRegion(logRegion));
        }
        this.awsLogs = awsLogs;
    }

    public synchronized void start() {
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

    public synchronized void stop() {
        try {
            awsLogs.shutdown();
        } catch (Exception e) {
            // ignore
        }
    }

    public synchronized void logEvents(Collection<InputLogEvent> events) {
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
            logEvents(events);
        }
    }
}
