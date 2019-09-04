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
    private final Lazy<AWSLogs> lazyAwsLogs = new Lazy<>();

    AWSLogsStub(final String logGroupName, final String logStreamName, final String logRegion) {
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;
        this.logRegion = logRegion;
    }

    private AWSLogs awsLogs() {
        return lazyAwsLogs.getOrCompute(() -> {
            System.out.println("Creating AWSLogs Client");
            final AWSLogsClientBuilder builder = AWSLogsClientBuilder.standard();
            Optional.ofNullable(logRegion).ifPresent(builder::setRegion);

            final AWSLogs awsLogs = builder.build();
            initLogGroup(awsLogs);
            return awsLogs;
        });
    }

    private void initLogGroup(final AWSLogs awsLogs) {
		if(!existsAwsLogGroup(awsLogs)) {
			System.out.println("Creating LogGroup: " + logGroupName);
			awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(logGroupName));
		}
		
		if(!existsAwsLogStream(awsLogs)) {
			System.out.println("Creating LogStream: " + logStreamName);
			awsLogs.createLogStream(new CreateLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
		}
    }

	private boolean existsAwsLogStream(final AWSLogs awsLogs) {
		final DescribeLogStreamsRequest describleLogStreamsRequest = new DescribeLogStreamsRequest(logGroupName).withLogStreamNamePrefix(logStreamName);
		final List<LogStream> logStreams = awsLogs.describeLogStreams(describleLogStreamsRequest).getLogStreams();
		return logStreams.stream().anyMatch(logStream -> logStream.getLogStreamName().equals(logStreamName));
	}

	private boolean existsAwsLogGroup(final AWSLogs awsLogs) {
		final DescribeLogGroupsRequest describeLogGroupsRequest = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName);
		final List<LogGroup> logGroups = awsLogs.describeLogGroups(describeLogGroupsRequest).getLogGroups();
		return logGroups.stream().anyMatch(logGroup -> logGroup.getLogGroupName().equals(logGroupName));
	}

    synchronized void start() {
    }

    synchronized void stop() {
        try {
            awsLogs().shutdown();
        } catch (final Exception e) {
            // ignore
        }
    }

    synchronized void logEvents(Collection<InputLogEvent> events) {
        if (events.size() > 1) {
            final List<InputLogEvent> sortedEvents = new ArrayList<InputLogEvent>(events);
            Collections.sort(sortedEvents, inputLogEventByTimestampComparator);
            events = sortedEvents;
        }
        for (final InputLogEvent event : events) {
            if (lastTimestamp != null && event.getTimestamp() < lastTimestamp) {
                event.setTimestamp(lastTimestamp);
            } else {
                lastTimestamp = event.getTimestamp();
            }
        }
        logPreparedEvents(events);
    }

    private void logPreparedEvents(final Collection<InputLogEvent> events) {
        try {
            final PutLogEventsRequest request = new PutLogEventsRequest()
                    .withLogGroupName(logGroupName)
                    .withLogStreamName(logStreamName)
                    .withSequenceToken(sequenceToken)
                    .withLogEvents(events);
            final PutLogEventsResult result = awsLogs().putLogEvents(request);
            sequenceToken = result.getNextSequenceToken();
        } catch (final DataAlreadyAcceptedException e) {
            sequenceToken = e.getExpectedSequenceToken();
        } catch (final InvalidSequenceTokenException e) {
            sequenceToken = e.getExpectedSequenceToken();
            logPreparedEvents(events);
        }
    }
}
