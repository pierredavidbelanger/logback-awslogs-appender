[![Build Status](https://travis-ci.org/pierredavidbelanger/logback-awslogs-appender.svg?branch=master)](https://travis-ci.org/pierredavidbelanger/logback-awslogs-appender)

# Logback AWSLogs appender

An [appender](http://logback.qos.ch/manual/appenders.html) for [Logback](http://logback.qos.ch/) to ship logs to [Amazon Web Services](https://aws.amazon.com).

Supports shipping to:
* [CloudWatch Logs](http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/Welcome.html)
* [S3 buckets](https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html)

Thank you for your help:
- [ivanfmartinez](https://github.com/ivanfmartinez)
- [jochenschneider](https://github.com/jochenschneider)
- [malkusch](https://github.com/malkusch)
- [robertoestivill](https://github.com/robertoestivill)
- [dnovitski](https://github.com/dnovitski)

## Quick start

### `pom.xml`:

```xml
<project>
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.21</version>
        </dependency>
        <dependency>
            <groupId>io.github.dnovitski</groupId>
            <artifactId>logback-awslogs-appender</artifactId>
            <version>1.7.0</version>
        </dependency>
    </dependencies>
</project>
```

### `logback.xml`

The simplest config that actually (synchronously) send logs to CloudWatch (see [More configurations section](#more-configurations) for a real life example):

```xml
<configuration>

    <appender name="AWS_LOGS" class="ca.pjer.logback.AwsLogsAppender"/>

    <root>
        <appender-ref ref="AWS_LOGS"/>
    </root>

</configuration>
```

With every possible defaults:
- The Layout will default to [EchoLayout](http://logback.qos.ch/apidocs/ch/qos/logback/core/layout/EchoLayout.html).
- The Log Group Name will default to `AwsLogsAppender`.
- The Log Stream Name will default to a timestamp formated with `yyyyMMdd'T'HHmmss`.
- The AWS Region will default to the AWS SDK default region (`us-east-1`) or the current instance region.
- The `maxFlushTimeMillis` will default to `0`, so appender is in synchronous mode.

`AwsLogsAppender` will search for AWS Credentials using the [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html).

The foud Credentials must have at least this [Role Policy](http://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_manage.html):

```json
{
  "Statement": [
    {
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

### Code

As usual with [SLF4J](http://www.slf4j.org/):

```java
// get a logger
org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MyClass.class);

// log
logger.info("HelloWorld");
```

## More configurations

It may be worth quoting this from _AWS_, because this is why we need to have unique `logStreamName`:

> When you have two processes attempting to perform the PutLogEvents API call to the same log stream, there is a chance that one will pass and one will fail because of the sequence token provided for that same log stream. Because of the sequencing of these events maintained in the log stream, you cannot have concurrently running processes pushing to the same log-stream.

A real life `logback.xml` would probably look like this (when all options are specified):

```xml
<configuration packagingData="true">

    <!-- Register the shutdown hook to allow logback to cleanly stop appenders -->
    <!-- this is strongly recommend when using AwsLogsAppender in async mode, -->
    <!-- to allow the queue to flush on exit -->
    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>

    <!-- Timestamp used into the Log Stream Name -->
    <timestamp key="timestamp" datePattern="yyyyMMddHHmmssSSS"/>

    <!-- The actual AwsLogsAppender (asynchronous mode because of maxFlushTimeMillis > 0) -->
    <appender name="ASYNC_AWS_LOGS" class="ca.pjer.logback.AwsLogsAppender">

        <!-- Send only WARN and above -->
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>WARN</level>
        </filter>

        <!-- Nice layout pattern -->
        <layout>
            <pattern>%d{yyyyMMdd'T'HHmmss} %thread %level %logger{15} %msg%n</pattern>
        </layout>

        <!-- Hardcoded Log Group Name -->
        <logGroupName>/com/acme/myapp</logGroupName>

        <!-- Log Stream Name UUID Prefix -->
        <logStreamUuidPrefix>mystream/</logStreamUuidPrefix>

        <!-- Or alternatively, Log Stream Name via pattern generated upon application startup -->
        <!-- Supported tokens are:
          %{uuid} - Random generated UUID
          %{datetime} - Current date in yyyymmddhhmmss format -->
        <logStreamNamePattern>mystream/%{uuid}</logStreamNamePattern>
 
        <!-- Hardcoded AWS region -->
        <!-- So even when running inside an AWS instance in us-west-1, logs will go to us-west-2 -->
        <logRegion>us-west-2</logRegion>

        <!-- Maximum number of events in each batch (50 is the default) -->
        <!-- will flush when the event queue has 50 elements, even if still in quiet time (see maxFlushTimeMillis) -->
        <maxBatchLogEvents>50</maxBatchLogEvents>

        <!-- Maximum quiet time in millisecond (0 is the default) -->
        <!-- will flush when met, even if the batch size is not met (see maxBatchLogEvents) -->
        <maxFlushTimeMillis>30000</maxFlushTimeMillis>

        <!-- Maximum block time in millisecond (5000 is the default) -->
        <!-- when > 0: this is the maximum time the logging thread will wait for the logger, -->
        <!-- when == 0: the logging thread will never wait for the logger, discarding events while the queue is full -->
        <maxBlockTimeMillis>5000</maxBlockTimeMillis>

        <!-- Retention value for log groups, 0 for infinite see -->
        <!-- https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html for other -->
        <!-- possible values -->
        <retentionTimeDays>0</retentionTimeDays>

        <!-- Use custom credential instead of DefaultCredentialsProvider -->
        <accessKeyId>YOUR_ACCESS_KEY_ID</accessKeyId>
        <secretAccessKey>YOUR_SECRET_ACCESS_KEY</secretAccessKey>
        
        <!-- Alternatively, to upload logs to S3 buckets, configure a bucket name and bucket path -->
        <bucketName>mybucket</bucketName>
        <!-- Supported tokens are:
            %{log_group} - The log group specified above
            %{log_stream} - The log stream specified above
            %{date} - Current date in yyyy-mm-dd format
            %{uuid} - Random generated UUID, unique for each call
            %{millis} - Current system timestamp in millis
            %{nanos} - Current system timestamp in nanos
            %{counter} - Atomic counter incremented each call
            
            Log files uploaded to S3 will be JSON files using "Records" JSON array format supported by Filebeat. 
        -->
        <bucketPath>logs/log_group=%{log_group}/date=%{date}/log_stream=%{log_stream}/%{counter}.log</bucketPath>
      
        <!-- Use 's3' to upload to S3 buckets, otherwise 'cloudwatch' is the default -->
        <logOutputType>s3</logOutputType>

        <!-- Use 'json' to specify logs are in json format, 'text' for plaintext, and nothing to autodetect when needed (default) --> 
        <logFormatType>text</logFormatType>
    </appender>

    <!-- A console output -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyyMMdd'T'HHmmss} %thread %level %logger{15} %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Root with a threshold to INFO and above -->
    <root level="INFO">
        <!-- Append to the console -->
        <appender-ref ref="STDOUT"/>
        <!-- Append also to the (async) AwsLogsAppender -->
        <appender-ref ref="ASYNC_AWS_LOGS"/>
    </root>

</configuration>
```

See [The logback manual - Chapter 3: Logback configuration](http://logback.qos.ch/manual/configuration.html) for more config options.
