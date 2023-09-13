package ca.pjer.logback.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public final class CompressionUtilility {
    public static byte[] compressGzip(byte[] bytes, int level) {
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        try (ConfigurableGZIPOutputStream gzip = new ConfigurableGZIPOutputStream(obj).withLevel(level)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return obj.toByteArray();
    }

    private static class ConfigurableGZIPOutputStream extends GZIPOutputStream {
        public ConfigurableGZIPOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        public ConfigurableGZIPOutputStream withLevel(int level) {
            def.setLevel(level);
            return this;
        }
    }
}