package ca.pjer.logback.client;

import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;

import java.util.Collection;

public interface AwsLogsClient {
    void close();

    void sendLogs(Collection<InputLogEvent> events);
}
