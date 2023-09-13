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
    private final String s3FileFormat;
    private final Integer s3FileCompressionLevel;

    public AwsLogsClientProperties(String logGroupName, String logStreamName, String logRegion, int retentionTimeInDays, String cloudWatchEndpoint, boolean verbose, String accessKeyId, String secretAccessKey, String bucketName, String bucketPath, String logFormatType, String logOutputType, ZoneId zoneId, String s3FileFormat, Integer s3FileCompressionLevel) {
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
        this.s3FileFormat = s3FileFormat;
        this.s3FileCompressionLevel = s3FileCompressionLevel;
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

    // Specifies the input log event format, eg text, json, or autodetect (null)
    public String getLogFormatType() {
        return logFormatType;
    }

    // Specifies the output type: where to ship logs to, eg s3 or cloudwatch
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

    // Specifies the s3 file format, eg json records array, json hive
    public String getS3FileFormat() {
        return s3FileFormat;
    }

    public Integer getS3FileCompressionLevel() {
        return s3FileCompressionLevel == null ? 1 : s3FileCompressionLevel;
    }
}
