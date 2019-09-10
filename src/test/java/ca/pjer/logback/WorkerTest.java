package ca.pjer.logback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.amazonaws.services.logs.model.InputLogEvent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.layout.EchoLayout;

@RunWith(Theories.class)
public class WorkerTest {

    private static final Layout<ILoggingEvent> LAYOUT = new EchoLayout<ILoggingEvent>();
    private static final int LAYOUT_OFFSET = LAYOUT.doLayout(asEvent("")).length();
    private static final int MESSAGE_SIZE_LIMIT = 262144 - 26 - LAYOUT_OFFSET;
    private Worker<ILoggingEvent> worker;

    @Before
    public void setWorker() {
        AwsLogsAppender<ILoggingEvent> awsLogsAppender = new AwsLogsAppender<>();
        awsLogsAppender.setLayout(LAYOUT);
        worker = new SyncWorker<>(awsLogsAppender);
    }

    @DataPoints("UNTRIMMED")
    public static final String[] UNTRIMMED = {
            repeat("x", MESSAGE_SIZE_LIMIT),
            repeat("x", MESSAGE_SIZE_LIMIT - 2) + "ö" };

    @Theory
    public void eventShouldNotBeTrimmed(@FromDataPoints("UNTRIMMED") String message) {
        InputLogEvent event = worker.asInputLogEvent(asEvent(message));
        assertFalse(event.getMessage().endsWith("..."));
    }

    @DataPoints("TRIMMED")
    public static final String[] TRIMMED = {
            repeat("x", MESSAGE_SIZE_LIMIT + 1),
            repeat("x", MESSAGE_SIZE_LIMIT - 1) + "ö",
            repeat("x", MESSAGE_SIZE_LIMIT - 2) + "öö",
            repeat("x", MESSAGE_SIZE_LIMIT - 3) + "öö",
            repeat("x", MESSAGE_SIZE_LIMIT) + "ö" };

    @Theory
    public void eventShouldBeTrimmed(@FromDataPoints("TRIMMED") String message) {
        InputLogEvent event = worker.asInputLogEvent(asEvent(message));
        assertTrue(event.getMessage().endsWith("..."));
    }

    @DataPoints("TRIMMED_MB")
    public static final String[] TRIMMED_MB = {
            repeat("x", MESSAGE_SIZE_LIMIT - 9) + "öööööööööö",
            repeat("x", MESSAGE_SIZE_LIMIT - 8) + "öööööööööö",
            repeat("x", MESSAGE_SIZE_LIMIT - 7) + "öööööööööö", };

    @Theory
    public void trimmingShouldNotChopMultibyteCharacter(@FromDataPoints("TRIMMED_MB") String message) {
        InputLogEvent event = worker.asInputLogEvent(asEvent(message));
        assertTrue(event.getMessage().endsWith("ö..."));
    }

    @Theory
    public void eventShouldNeverExceed262144Bytes(String message) throws UnsupportedEncodingException {
        InputLogEvent event = worker.asInputLogEvent(asEvent(message));
        int eventSize = event.getMessage().getBytes("UTF-8").length + 26;
        assertTrue(eventSize <= 262144);
    }

    private static ILoggingEvent asEvent(String message) {
        LoggerContext loggerContext = new LoggerContext();
        return new LoggingEvent(null, loggerContext.getLogger(WorkerTest.class), Level.INFO, message, null, null);
    }

    private static String repeat(String value, int count) {
        return new String(new char[count]).replace("\0", value);
    }

}
