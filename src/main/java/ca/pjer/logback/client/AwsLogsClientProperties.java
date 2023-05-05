package ca.pjer.logback.client;

import java.time.ZoneId;

public class AwsLogsClientProperties {
    private final String logGroupName;
    private final String logStreamName;
    private final String logRegion;
    private final String logFormatType;
    private final String logOutputType;
    private final String endpoint;
    private final boolean verbose;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final int retentionTimeInDays;
    private final String bucketName;
    private final String bucketPath;
    private final ZoneId zoneId;

    public AwsLogsClientProperties(String logGroupName, String logStreamName, String logRegion, int retentionTimeInDays, String cloudWatchEndpoint, boolean verbose, String accessKeyId, String secretAccessKey, String bucketName, String bucketPath, String logFormatType, String logOutputType, ZoneId zoneId) {
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;
        this.logRegion = logRegion;
        this.retentionTimeInDays = retentionTimeInDays;
        this.endpoint = cloudWatchEndpoint;
        this.verbose = verbose;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.bucketName = bucketName;
        this.bucketPath = bucketPath;
        this.logFormatType = logFormatType;
        this.logOutputType = logOutputType;
        this.zoneId = zoneId;
    }

    public String getLogGroupName() {
        return logGroupName;
    }

    public String getLogStreamName() {
        return logStreamName;
    }

    public String getLogRegion() {
        return logRegion;
    }

    public String getLogFormatType() {
        return logFormatType;
    }

    public String getLogOutputType() {
        return logOutputType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public int getRetentionTimeInDays() {
        return retentionTimeInDays;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getBucketPath() {
        return bucketPath;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
