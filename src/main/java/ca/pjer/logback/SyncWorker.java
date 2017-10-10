package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Collections;

class SyncWorker extends Worker {

    SyncWorker(AwsLogsAppender awsLogsAppender) {
        super(awsLogsAppender);
    }

    @Override
    public synchronized void append(ILoggingEvent event) {
        getAwsLogsAppender().getAwsLogsStub().logEvents(Collections.singleton(asInputLogEvent(event)));
    }
}
