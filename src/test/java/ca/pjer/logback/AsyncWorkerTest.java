package ca.pjer.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.layout.EchoLayout;
import com.amazonaws.services.logs.model.InputLogEvent;
import org.junit.Test;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;

public class AsyncWorkerTest {

    private static Collection<InputLogEvent> anyInputLogEvents() {
        return anyCollection();
    }

    private static final AtomicLong timestamp = new AtomicLong(System.currentTimeMillis());

    private static ILoggingEvent dummyEvent() {
        LoggerContext loggerContext = new LoggerContext();
        LoggingEvent event = new LoggingEvent(AsyncWorkerTest.class.getName(), loggerContext.getLogger(AsyncWorkerTest.class.getName()), Level.WARN, "Dummy " + UUID.randomUUID().toString(), null, null);
        event.setTimeStamp(timestamp.getAndIncrement());
        return event;
    }

    private static AsyncWorker<ILoggingEvent> asyncWorker(AWSLogsStub mockedAwsLogsStub, int maxBatchLogEvents, long maxFlushTimeMillis, long maxBlockTimeMillis) {
        AwsLogsAppender<ILoggingEvent> awsLogsAppender = new AwsLogsAppender<>();
        awsLogsAppender.setLayout(new EchoLayout<>());
        awsLogsAppender.setLogGroupName("FakeGroup");
        awsLogsAppender.setLogStreamName("FakeStream");
        awsLogsAppender.setMaxBatchLogEvents(maxBatchLogEvents);
        awsLogsAppender.setMaxFlushTimeMillis(maxFlushTimeMillis);
        awsLogsAppender.setMaxBlockTimeMillis(maxBlockTimeMillis);
        awsLogsAppender.setAwsLogsStub(mockedAwsLogsStub);
        AsyncWorker<ILoggingEvent> asyncWorker = new AsyncWorker<>(awsLogsAppender);
        awsLogsAppender.setWorker(asyncWorker);
        return asyncWorker;
    }

    @Test
    public void testShouldNotLogWhenStopped() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker<ILoggingEvent> asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1, 5000);
        asyncWorker.start();
        asyncWorker.stop();
        asyncWorker.append(dummyEvent());
        verify(mockedAwsLogsStub, never()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldLogWhenStarted() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker<ILoggingEvent> asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1, 5000);
        asyncWorker.start();
        asyncWorker.append(dummyEvent());
        asyncWorker.stop();
        verify(mockedAwsLogsStub, atLeastOnce()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldLogAfterMaxBatchSize() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker<ILoggingEvent> asyncWorker = asyncWorker(mockedAwsLogsStub, 5, Long.MAX_VALUE, 5000);
        asyncWorker.start();
        asyncWorker.append(dummyEvent());
        asyncWorker.append(dummyEvent());
        asyncWorker.append(dummyEvent());
        asyncWorker.append(dummyEvent());
        verify(mockedAwsLogsStub, never()).logEvents(anyInputLogEvents());
        asyncWorker.append(dummyEvent());
        asyncWorker.stop();
        verify(mockedAwsLogsStub, atLeastOnce()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldNotLogEmptyAfterMaxFlushTimeMillis() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1, 5000);
        asyncWorker.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            return;
        }
        verify(mockedAwsLogsStub, never()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldLogAfterMaxFlushTimeMillis() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker<ILoggingEvent> asyncWorker = asyncWorker(mockedAwsLogsStub, 5, 1000, 5000);
        asyncWorker.start();
        asyncWorker.append(dummyEvent());
        verify(mockedAwsLogsStub, after(1500)).logEvents(anyInputLogEvents());
    }
}
