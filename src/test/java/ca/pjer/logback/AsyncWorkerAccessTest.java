package ca.pjer.logback;

import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.access.spi.ServerAdapter;
import ch.qos.logback.core.layout.EchoLayout;
import com.amazonaws.services.logs.model.InputLogEvent;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;

import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AsyncWorkerAccessTest {

    private static Collection<InputLogEvent> anyInputLogEvents() {
        return anyCollection();
    }

    private HttpServletRequest request = mock(HttpServletRequest.class);
    private HttpServletResponse response = mock(HttpServletResponse.class);
    private ServerAdapter serverAdapter = mock(ServerAdapter.class);

    private static AsyncWorker<IAccessEvent> asyncWorker(AWSLogsStub mockedAwsLogsStub) {
        AwsLogsAppender<IAccessEvent> awsLogsAppender = new AwsLogsAppender<>();
        awsLogsAppender.setLayout(new EchoLayout<>());
        awsLogsAppender.setLogGroupName("FakeGroup");
        awsLogsAppender.setLogStreamName("FakeStream");
        awsLogsAppender.setMaxBatchLogEvents(1);
        awsLogsAppender.setMaxFlushTimeMillis(1);
        awsLogsAppender.setMaxBlockTimeMillis(5000);
        awsLogsAppender.setAwsLogsStub(mockedAwsLogsStub);
        AsyncWorker<IAccessEvent> asyncWorker = new AsyncWorker<>(awsLogsAppender);
        awsLogsAppender.setWorker(asyncWorker);
        return asyncWorker;
    }

    @Test
    public void testShouldLogWhenStarted() {
        AWSLogsStub mockedAwsLogsStub = mock(AWSLogsStub.class);
        IAccessEvent event = spy(new AccessEvent(request, response, serverAdapter));
        AsyncWorker<IAccessEvent> asyncWorker = asyncWorker(mockedAwsLogsStub);
        asyncWorker.start();
        asyncWorker.append(event);
        asyncWorker.stop();

        verify(event).getTimeStamp();
        verify(mockedAwsLogsStub).logEvents(anyInputLogEvents());
    }
}
