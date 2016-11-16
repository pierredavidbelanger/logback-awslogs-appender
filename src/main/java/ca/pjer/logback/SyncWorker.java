package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;

import java.util.Collections;

class SyncWorker extends Worker {

    SyncWorker(AWSLogsStub awsLogsStub) {
        super(awsLogsStub);
    }

    @Override
    public synchronized void append(InputLogEvent event) {
        getAwsLogsStub().logEvents(Collections.singleton(event));
    }
}
