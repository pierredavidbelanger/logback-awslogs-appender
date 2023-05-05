package ca.pjer.logback;

import ca.pjer.logback.client.AwsLogsClient;
import ca.pjer.logback.client.AwsLogsClientProperties;
import ca.pjer.logback.client.AwsLogsCloudWatchClient;
import ca.pjer.logback.client.AwsLogsS3Client;
import ca.pjer.logback.metrics.AwsLogsMetricsHolder;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;

import java.util.*;

class AWSLogsStub {
    private static final String LOG_OUTPUT_TYPE_S3 = "s3";
    private static final String LOG_OUTPUT_TYPE_CLOUDWATCH = "cloudwatch";

    private final Comparator<InputLogEvent> inputLogEventByTimestampComparator = Comparator.comparing(InputLogEvent::timestamp);
    private Long lastTimestamp;

    private final Lazy<AwsLogsClient> lazyAwsLogsClient = new Lazy<>();
    private final AwsLogsClientProperties properties;

    AWSLogsStub(AwsLogsClientProperties properties) {
        this.properties = properties;
    }

    private AwsLogsClient awsLogs() {
        return lazyAwsLogsClient.getOrCompute(() -> {
            if (properties.isVerbose()) {
                System.out.println("Creating AWSLogs Client");
            }

            if (LOG_OUTPUT_TYPE_S3.equals(properties.getLogOutputType())) {
                return new AwsLogsS3Client(properties);
            } else if (LOG_OUTPUT_TYPE_CLOUDWATCH.equals(properties.getLogOutputType()) || Objects.isNull(properties.getLogOutputType())) {
                return new AwsLogsCloudWatchClient(properties);
            } else {
                throw new RuntimeException("Unknown log output type: " + properties.getLogOutputType());
            }
        });
    }


    synchronized void start() {
    }

    synchronized void stop() {
        try {
            AwsLogsClient awsLogsClient = lazyAwsLogsClient.get();
            if (awsLogsClient != null) {
                if (properties.isVerbose()) {
                    System.out.println("Closing AWSLogs Client");
                }
                awsLogsClient.close();
            }
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

        ArrayList<InputLogEvent> correctedEvents = new ArrayList<InputLogEvent>(events.size());
        for (InputLogEvent event : events) {
            if (lastTimestamp != null && event.timestamp() < lastTimestamp) {
                correctedEvents.add(event.toBuilder()
                        .timestamp(lastTimestamp)
                        .build());
            } else {
                correctedEvents.add(event);
                lastTimestamp = event.timestamp();
            }
        }
        AwsLogsMetricsHolder.get().incrementLogEvents(correctedEvents.size());
        AwsLogsMetricsHolder.get().incrementPutLog();
        sendLogEvents(correctedEvents);
    }

    private void sendLogEvents(Collection<InputLogEvent> events) {
        awsLogs().sendLogs(events);
    }
}
