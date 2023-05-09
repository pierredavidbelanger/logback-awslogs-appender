package ca.pjer.logback.client;

import ca.pjer.logback.metrics.AwsLogsMetricsHolder;
import ca.pjer.logback.tokenisation.TokenUtility;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class AwsLogsS3Client implements AwsLogsClient {
    private static final String RECORDS = "Records";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String DEFAULT_BUCKET_PATH = "logs/log_group=%{log_group}/date=%{date}/log_stream=%{log_stream}/%{uuid}.log";
    private static final String LOG_FORMAT_TYPE_JSON = "json";
    private final AwsLogsClientProperties properties;
    private final S3Client client;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final JsonFactory jsonFactory = new JsonFactory();
    private final AtomicLong counter = new AtomicLong(0);
    private final Map<String, Supplier<String>> bucketPathTokenSuppliers = new HashMap<>();

    public AwsLogsS3Client(AwsLogsClientProperties properties) {
        this.properties = properties;
        S3ClientBuilder builder = S3Client.builder();

        if (Objects.nonNull(properties.getEndpoint())) {
            try {
                builder = builder.endpointOverride(new URI(properties.getEndpoint()));
            } catch (URISyntaxException e) {
                if (properties.isVerbose()) {
                    System.out.println("Invalid endpoint URL: " + properties.getEndpoint());
                }
            }
        }

        if (Objects.nonNull(properties.getLogRegion())) {
            builder = builder.region(Region.of(properties.getLogRegion()));
        }

        if (Objects.nonNull(properties.getAccessKeyId()) && Objects.nonNull(properties.getSecretAccessKey())) {
            AwsCredentialsProvider credentialProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
            builder.credentialsProvider(credentialProvider);
        }

        client = builder.build();
        bucketPathTokenSuppliers.put("log_group", () -> properties.getLogGroupName());
        bucketPathTokenSuppliers.put("log_stream", () -> properties.getLogStreamName());
        bucketPathTokenSuppliers.put("date", () -> dateFormatter.format(Instant.now().atZone(properties.getZoneId())));
        bucketPathTokenSuppliers.put("uuid", () -> UUID.randomUUID().toString());
        bucketPathTokenSuppliers.put("millis", () -> String.format("%020d", System.currentTimeMillis()));
        bucketPathTokenSuppliers.put("nanos", () -> String.format("%020d", System.nanoTime()));
        bucketPathTokenSuppliers.put("counter", () -> String.format("%020d", counter.getAndIncrement()));
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public void sendLogs(Collection<InputLogEvent> events) {
        try {
            String objectKey = buildLogFilename();
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(objectKey)
                    .build();

            byte[] bytes = buildLogBytes(events);
            client.putObject(putOb, RequestBody.fromContentProvider(() -> new ByteArrayInputStream(bytes), bytes.length, CONTENT_TYPE_APPLICATION_JSON));
        } catch (Throwable t) {
            if (properties.isVerbose()) {
                t.printStackTrace();
            }
            AwsLogsMetricsHolder.get().incrementPutLogFailed(t);
            throw t;
        }
    }

    private byte[] buildLogBytes(Collection<InputLogEvent> events) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JsonGenerator jsonGenerator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName(RECORDS);
            jsonGenerator.writeStartArray();

            for (InputLogEvent event : events) {
                String message = event.message();
                if (isJsonFormat(message)) {
                    jsonGenerator.writeRawValue(message);
                } else { // Plain text lines probably
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("@timestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(event.timestamp()).atZone(properties.getZoneId())));
                    jsonGenerator.writeStringField("message", event.message());
                    jsonGenerator.writeEndObject();
                }
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
            jsonGenerator.close();

            return outputStream.toByteArray();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private boolean isJsonFormat(String message) {
        if (Objects.nonNull(properties.getLogFormatType())) {
            return LOG_FORMAT_TYPE_JSON.equals(properties.getLogOutputType());
        }

        if (!message.startsWith("{")) {
            return false;
        }

        return message.endsWith("}\n") || message.endsWith("}");
    }

    private String buildLogFilename() {
        String path = Objects.nonNull(properties.getBucketPath()) ? properties.getBucketPath() : DEFAULT_BUCKET_PATH;
        return TokenUtility.replaceTokens(path, bucketPathTokenSuppliers);
    }
}
