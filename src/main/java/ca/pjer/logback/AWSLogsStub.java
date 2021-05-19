package ca.pjer.logback;

import ca.pjer.logback.metrics.AwsLogsMetricsHolder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClientBuilder;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

class AWSLogsStub {
    private final Comparator<InputLogEvent> inputLogEventByTimestampComparator = Comparator.comparing(InputLogEvent::timestamp);
    private final String logGroupName;
    private final String logStreamName;
    private final String logRegion;
    private final String cloudWatchEndpoint;
    private final boolean verbose;
    private String sequenceToken;
    private Long lastTimestamp;
    private int retentionTimeInDays;

    private final Lazy<CloudWatchLogsClient> lazyAwsLogs = new Lazy<>();

    AWSLogsStub(String logGroupName, String logStreamName, String logRegion, int retentionTimeInDays, String cloudWatchEndpoint, boolean verbose) {
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;
        this.logRegion = logRegion;
        this.retentionTimeInDays = retentionTimeInDays;
        this.cloudWatchEndpoint = cloudWatchEndpoint;
        this.verbose = verbose;
    }


    private CloudWatchLogsClient awsLogs() {
        return lazyAwsLogs.getOrCompute(() -> {
            if (verbose) {
                System.out.println("Creating AWSLogs Client");
            }

            CloudWatchLogsClientBuilder builder = CloudWatchLogsClient.builder();

            if (Objects.nonNull(cloudWatchEndpoint)) {
                try {
                    builder = builder.endpointOverride(new URI(cloudWatchEndpoint));
                } catch (URISyntaxException e) {
                    if (verbose) {
                        System.out.println("Invalid endpoint endpoint URL: "  + cloudWatchEndpoint);
                    }
                }
            }

            if (Objects.nonNull(logRegion)) {
                builder = builder.region(Region.of(logRegion));
            }

            CloudWatchLogsClient awsLogs = builder.build();
            initLogGroup(awsLogs);
            return awsLogs;
        });
    }

    private void initLogGroup(CloudWatchLogsClient awsLogs) {
        try {
            awsLogs.createLogGroup(CreateLogGroupRequest.builder()
                    .logGroupName(logGroupName)
                    .build());
            if(retentionTimeInDays > 0) {
                awsLogs.putRetentionPolicy(PutRetentionPolicyRequest.builder()
                        .logGroupName(logGroupName)
                        .retentionInDays(retentionTimeInDays)
                        .build());
            }
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        } catch (Throwable t) {
            if (verbose) {
                t.printStackTrace();
            }
        }
        try {
            awsLogs.createLogStream(CreateLogStreamRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .build());
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        } catch (Throwable t) {
            if (verbose) {
                t.printStackTrace();
            }
        }
    }

    synchronized void start() {
    }

    synchronized void stop() {
        try {
            awsLogs().close();
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
            if (lastTimestamp != null && event.timestamp() < lastTimestamp) {
                events.remove(event);
                events.add(event.toBuilder()
                    .timestamp(lastTimestamp)
                    .build());
            } else {
                lastTimestamp = event.timestamp();
            }
        }
        AwsLogsMetricsHolder.get().incrementLogEvents(events.size());
        AwsLogsMetricsHolder.get().incrementPutLog();
        logPreparedEvents(events);
    }

    private void logPreparedEvents(Collection<InputLogEvent> events) {
        try {
            PutLogEventsRequest request = PutLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .sequenceToken(sequenceToken)
                    .logEvents(events)
                    .build();
            PutLogEventsResponse result = awsLogs().putLogEvents(request);
            sequenceToken = result.nextSequenceToken();
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken = e.expectedSequenceToken();
        } catch (InvalidSequenceTokenException e) {
            sequenceToken = e.expectedSequenceToken();
            logPreparedEvents(events);
        } catch (Throwable t) {
            if (verbose) {
                t.printStackTrace();
            }
            AwsLogsMetricsHolder.get().incrementPutLogFailed(t);
            throw t;
        }
    }
}
