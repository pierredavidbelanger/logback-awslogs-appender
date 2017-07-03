# Logback AWSLogs appender

An [Amazon Web Services](https://aws.amazon.com) [CloudWatch Logs](http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/Welcome.html) [appender](http://logback.qos.ch/manual/appenders.html) for [Logback](http://logback.qos.ch/).

## Quick start

### `pom.xml`:

```xml
<project>
    ...
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.21</version>
        </dependency>
        <dependency>
            <groupId>ca.pjer</groupId>
            <artifactId>logback-awslogs-appender</artifactId>
            <version>0.1.1</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>
```

### `logback.xml`

The simplest config that actually send logs to CloudWatch (see [More configurations section](#more-configurations) for a real life example):

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

A real life `logback.xml` would probably look like this:

```xml
<configuration packagingData="true">

    <!-- Timestamp used into the Log Stream Name -->
    <timestamp key="date" datePattern="yyyyMMdd"/>

    <!-- The actual AwsLogsAppender (synchronous) -->
    <appender name="AWS_LOGS" class="ca.pjer.logback.AwsLogsAppender">
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
        <!-- Timestamped Log Stream Name -->
        <logStreamName>mystream-${date}</logStreamName>
    </appender>

    <!-- AsyncAppender that forward to AwsLogsAppender -->
    <appender name="ASYNC_AWS_LOGS" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="AWS_LOGS"/>
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
