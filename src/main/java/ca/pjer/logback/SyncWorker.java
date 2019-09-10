package ca.pjer.logback;

import java.util.Collections;

class SyncWorker<E> extends Worker<E> {

    SyncWorker(AwsLogsAppender<E> awsLogsAppender) {
        super(awsLogsAppender);
    }

    @Override
    public synchronized void append(E event) {
        getAwsLogsAppender().getAwsLogsStub().logEvents(Collections.singleton(asInputLogEvent(event)));
    }
}
