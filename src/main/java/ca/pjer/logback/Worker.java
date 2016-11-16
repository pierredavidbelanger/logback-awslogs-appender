package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;

abstract class Worker {

    private AWSLogsStub awsLogsStub;

    Worker(AWSLogsStub awsLogsStub) {
        this.awsLogsStub = awsLogsStub;
    }

    AWSLogsStub getAwsLogsStub() {
        return awsLogsStub;
    }

    public synchronized void start() {
    }

    public synchronized void stop() {
    }

    public abstract void append(InputLogEvent event);
}
