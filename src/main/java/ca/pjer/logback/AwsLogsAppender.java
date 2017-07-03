package ca.pjer.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.WarnStatus;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Date;
import java.util.List;

public class AwsLogsAppender extends AppenderBase<ILoggingEvent> {

    private Layout<ILoggingEvent> layout;

    private String logGroupName;
    private String logStreamName;
    private String logRegion;

    private AWSLogs awsLogs;
    private String sequenceToken;

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    public String getLogGroupName() {
        return logGroupName;
    }

    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
    }

    public String getLogStreamName() {
        return logStreamName;
    }

    public void setLogStreamName(String logStreamName) {
        this.logStreamName = logStreamName;
    }

    public String getLogRegion() {
        return logRegion;
    }
    
    public void setLogRegion(String logRegion) {
        this.logRegion = logRegion;
    }
    
    @Override
    public synchronized void start() {
        if (!isStarted()) {
            if (layout == null) {
                layout = new EchoLayout<ILoggingEvent>();
                addStatus(new WarnStatus("No layout, default to " + layout, this));
            }
            if (logGroupName == null) {
                logGroupName = getClass().getSimpleName();
                addStatus(new WarnStatus("No logGroupName, default to " + logGroupName, this));
            }
            if (logStreamName == null) {
                logStreamName = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());
                addStatus(new WarnStatus("No logStreamName, default to " + logStreamName, this));
            }
            try {
                if (this.awsLogs == null) {
                    AWSLogs awsLogs = new AWSLogsClient();
                    if (logRegion != null) {
                        awsLogs.setRegion(RegionUtils.getRegion(logRegion));
                    }
                    try {
                        awsLogs.createLogGroup(new CreateLogGroupRequest().withLogGroupName(logGroupName));
                    } catch (ResourceAlreadyExistsException e) {
                        addStatus(new InfoStatus(e.getMessage(), this));
                    }
                    try {
                        awsLogs.createLogStream(new CreateLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
                    } catch (ResourceAlreadyExistsException e) {
                        addStatus(new InfoStatus(e.getMessage(), this));
                    }
                    this.awsLogs = awsLogs;
                    layout.start();
                    super.start();
                }
            } catch (AmazonClientException e) {
                this.awsLogs = null;
                addStatus(new ErrorStatus(e.getMessage(), this, e));
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (isStarted()) {
            if (awsLogs != null) {
                awsLogs.shutdown();
                awsLogs = null;
            }
            super.stop();
            layout.stop();
        }
    }
    
    private final List<InputLogEvent> backLog = new LinkedList<InputLogEvent>();

    @Override
    protected void append(ILoggingEvent event) {
        final InputLogEvent inputLogEvent = new InputLogEvent()
                .withTimestamp(event.getTimeStamp())
                .withMessage(layout.doLayout(event));
        try {
            
            final PutLogEventsRequest request = new PutLogEventsRequest()
                    .withLogGroupName(logGroupName)
                    .withLogStreamName(logStreamName)
                    .withSequenceToken(sequenceToken);
            if (backLog.size() > 0) {
                request.withLogEvents(backLog);
            }
            request.withLogEvents(inputLogEvent);
            final PutLogEventsResult result = awsLogs.putLogEvents(request);
            sequenceToken = result.getNextSequenceToken();
            backLog.clear();
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken = e.getExpectedSequenceToken();
        } catch (InvalidSequenceTokenException e) {
            sequenceToken = e.getExpectedSequenceToken();
            append(event);
        } catch (AmazonServiceException e) {
            backLog.add(inputLogEvent);
            // when sending too fast CloudWatch returns ThrottlingException
            if ("ThrottlingException".equals(e.getErrorCode())) {
                //TODO put next events in backlog to lower the sending rate....
            }
            addStatus(new InfoStatus(e.getMessage(), this));
        }
    }
}
