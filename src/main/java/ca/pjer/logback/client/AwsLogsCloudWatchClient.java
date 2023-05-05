package ca.pjer.logback.client;

import ca.pjer.logback.metrics.AwsLogsMetricsHolder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClientBuilder;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Objects;

public class AwsLogsCloudWatchClient implements AwsLogsClient {
    private final AwsLogsClientProperties properties;
    private final CloudWatchLogsClient client;
    private String sequenceToken;

    public AwsLogsCloudWatchClient(AwsLogsClientProperties properties) {
        this.properties = properties;
        CloudWatchLogsClientBuilder builder = CloudWatchLogsClient.builder();

        if (Objects.nonNull(properties.getEndpoint())) {
            try {
                builder = builder.endpointOverride(new URI(properties.getEndpoint()));
            } catch (URISyntaxException e) {
                if (properties.isVerbose()) {
                    System.out.println("Invalid endpoint endpoint URL: " + properties.getEndpoint());
                }
            }
        }

        if (Objects.nonNull(properties.getLogRegion())) {
            builder = builder.region(Region.of(properties.getLogRegion()));
        }

        if (Objects.nonNull(properties.getAccessKeyId()) && Objects.nonNull(properties.getSecretAccessKey())) {
            AwsCredentialsProvider credentialProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
            builder.credentialsProvider(credentialProvider);
        }

        client = builder.build();
        initLogGroup(client);
    }

    private void initLogGroup(CloudWatchLogsClient awsLogs) {
        try {
            awsLogs.createLogGroup(CreateLogGroupRequest.builder()
                    .logGroupName(properties.getLogGroupName())
                    .build());
            if (properties.getRetentionTimeInDays() > 0) {
                awsLogs.putRetentionPolicy(PutRetentionPolicyRequest.builder()
                        .logGroupName(properties.getLogGroupName())
                        .retentionInDays(properties.getRetentionTimeInDays())
                        .build());
            }
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        } catch (Throwable t) {
            if (properties.isVerbose()) {
                t.printStackTrace();
            }
        }
        try {
            awsLogs.createLogStream(CreateLogStreamRequest.builder()
                    .logGroupName(properties.getLogGroupName())
                    .logStreamName(properties.getLogStreamName())
                    .build());
        } catch (ResourceAlreadyExistsException e) {
            // ignore
        } catch (Throwable t) {
            if (properties.isVerbose()) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public void sendLogs(Collection<InputLogEvent> events) {
        try {
            PutLogEventsRequest request = PutLogEventsRequest.builder()
                    .logGroupName(properties.getLogGroupName())
                    .logStreamName(properties.getLogStreamName())
                    .sequenceToken(sequenceToken)
                    .logEvents(events)
                    .build();
            PutLogEventsResponse result = client.putLogEvents(request);
            sequenceToken = result.nextSequenceToken();
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken = e.expectedSequenceToken();
        } catch (InvalidSequenceTokenException e) {
            sequenceToken = e.expectedSequenceToken();
            sendLogs(events);
        } catch (Throwable t) {
            if (properties.isVerbose()) {
                t.printStackTrace();
            }
            AwsLogsMetricsHolder.get().incrementPutLogFailed(t);
            throw t;
        }
    }
}
