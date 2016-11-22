package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.InvalidParameterException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class AsyncWorkerTest {

    private static Collection<InputLogEvent> anyInputLogEvents() {
        return anyCollection();
    }

    private static InputLogEvent dummyInputLogEvent() {
        return new InputLogEvent().withTimestamp(System.currentTimeMillis()).withMessage("Dummy " + UUID.randomUUID().toString());
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

    // @Test
    // This one is expected to fail, since the sorting is done in the AWSLogsStub
    public void testShouldLogInChronologicalOrder() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        final AtomicBoolean invalidParameterExceptionThrown = new AtomicBoolean(false);
        when(mockedAwsLogsStub.logEvents(anyInputLogEvents())).then(new Answer<AWSLogsStub>() {

            @Override
            public AWSLogsStub answer(InvocationOnMock invocationOnMock) throws Throwable {
                Collection<InputLogEvent> inputLogEvents = invocationOnMock.getArgument(0);
                Long lastTimestamp = null;
                for (Iterator<InputLogEvent> iterator = inputLogEvents.iterator(); iterator.hasNext(); ) {
                    InputLogEvent next = iterator.next();
                    if (lastTimestamp != null && lastTimestamp.compareTo(next.getTimestamp()) > 0) {
                        invalidParameterExceptionThrown.set(true);
                        throw new InvalidParameterException("Log events in a single PutLogEvents request must be in chronological order.");
                    }
                    lastTimestamp = next.getTimestamp();
                }
                return (AWSLogsStub) invocationOnMock.getMock();
            }
        });
        AsyncWorker asyncWorker = asyncWorker(mockedAwsLogsStub, 2, Long.MAX_VALUE);
        asyncWorker.start();
        long now = System.currentTimeMillis();
        InputLogEvent inputLogEvent1 = dummyInputLogEvent().withTimestamp(++now);
        InputLogEvent inputLogEvent2 = dummyInputLogEvent().withTimestamp(++now);
        asyncWorker.append(inputLogEvent2);
        asyncWorker.append(inputLogEvent1);
        asyncWorker.stop();
        verify(mockedAwsLogsStub, atLeastOnce()).logEvents(anyInputLogEvents());
        Assert.assertFalse(invalidParameterExceptionThrown.get());
    }
}
