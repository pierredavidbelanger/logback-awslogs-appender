package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;
import org.junit.Test;

import java.util.Collection;

import static org.mockito.Mockito.*;

public class AsyncWorkerTest {

    private static Collection<InputLogEvent> anyInputLogEvents() {
        return anyCollection();
    }

    private static InputLogEvent dummyInputLogEvent() {
        return new InputLogEvent().withTimestamp(System.currentTimeMillis()).withMessage("Dummy");
    }

    private static AsyncWorker asyncWorker(AWSLogsStub mockedAwsLogsStub, int maxQueueSize, long maxFlushTimeMillis) {
        return new AsyncWorker(mockedAwsLogsStub, "test", maxQueueSize, maxFlushTimeMillis);
    }

    @Test
    public void testShouldNotLogWhenStopped() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1);
        asyncWorker.start();
        asyncWorker.stop();
        asyncWorker.append(dummyInputLogEvent());
        verify(mockedAwsLogsStub, never()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldLogWhenStarted() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1);
        asyncWorker.start();
        asyncWorker.append(dummyInputLogEvent());
        asyncWorker.stop();
        verify(mockedAwsLogsStub, atLeastOnce()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldLogAfterMaxQueueSize() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 5, Long.MAX_VALUE);
        asyncWorker.start();
        asyncWorker.append(dummyInputLogEvent());
        asyncWorker.append(dummyInputLogEvent());
        asyncWorker.append(dummyInputLogEvent());
        asyncWorker.append(dummyInputLogEvent());
        verify(mockedAwsLogsStub, never()).logEvents(anyInputLogEvents());
        asyncWorker.append(dummyInputLogEvent());
        asyncWorker.stop();
        verify(mockedAwsLogsStub, atLeastOnce()).logEvents(anyInputLogEvents());
    }

    @Test
    public void testShouldNotLogEmptyAfterMaxFlushTimeMillis() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 1, 1);
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
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 5, 1000);
        asyncWorker.start();
        asyncWorker.append(dummyInputLogEvent());
        verify(mockedAwsLogsStub, after(1500)).logEvents(anyInputLogEvents());
    }
}
