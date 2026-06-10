package cn.qaiu.util;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.core.json.JsonObject;
//import org.brotli.dec.BrotliInputStream;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class HttpResponseHelper {
    static Logger LOGGER = LoggerFactory.getLogger(HttpResponseHelper.class);
    private static final int MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_CHARS = 16 * 1024 * 1024;

    // -------------------- 公共方法 --------------------
    public static String asText(HttpResponse<?> res) {
        String encoding = res.getHeader(HttpHeaders.CONTENT_ENCODING.toString());
        try {
            Buffer body = toBuffer(res);
            return asText(body, encoding);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("asText: {}", e.getMessage(), e);
            return null;
        }
    }

    public static String asText(Buffer body, String encoding) {
        try {
            if (body == null) {
                return "";
            }
            ensureBodyLimit(body);
            if (encoding == null || "identity".equalsIgnoreCase(encoding)) {
                return body.toString(StandardCharsets.UTF_8);
            }
            return decompress(body, encoding);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("asText: {}", e.getMessage(), e);
            return null;
        }
    }

    public static JsonObject asJson(HttpResponse<?> res) {
        try {
            String text = asText(res);
            return parseJsonText(text);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("asJson: {}", e.getMessage(), e);
            return JsonObject.of();
        }
    }

    public static JsonObject asJson(Buffer body, String encoding) {
        try {
            String text = asText(body, encoding);
            return parseJsonText(text);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("asJson: {}", e.getMessage(), e);
            return JsonObject.of();
        }
    }

    private static JsonObject parseJsonText(String text) {
        try {
            if (text != null) {
                return new JsonObject(text);
            } else  {
                LOGGER.error("asJson: asText响应数据为空");
                return JsonObject.of();
            }
        } catch (Exception e) {
            LOGGER.error("asJson: {}", e.getMessage(), e);
            return JsonObject.of();
        }
    }

    // -------------------- Buffer 转换 --------------------
    private static Buffer toBuffer(HttpResponse<?> res) {
        return res.body() instanceof Buffer ? (Buffer) res.body() : Buffer.buffer(res.bodyAsString());
    }

    private static void ensureBodyLimit(Buffer body) {
        if (body != null && body.length() > MAX_RESPONSE_BODY_BYTES) {
            throw new IllegalArgumentException("响应体过大: " + body.length() + " bytes");
        }
    }

    private static void writeLimited(StringWriter writer, char[] buffer, int len) throws IOException {
        if (writer.getBuffer().length() + len > MAX_DECOMPRESSED_CHARS) {
            throw new IOException("解压后响应体过大");
        }
        writer.write(buffer, 0, len);
    }

    // -------------------- 通用解压分发 --------------------
    private static String decompress(Buffer compressed, String encoding) throws IOException {
        return switch (encoding.toLowerCase()) {
            case "gzip" -> decompressGzip(compressed);
            case "deflate" -> decompressDeflate(compressed);
            case "br" -> decompressBrotli(compressed);
            case "zstd" -> throw new UnsupportedOperationException("不支持的 Content-Encoding: zstd");
            default -> throw new UnsupportedOperationException("不支持的 Content-Encoding: " + encoding);
        };
    }

    // -------------------- gzip --------------------
    private static String decompressGzip(Buffer compressed) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed.getBytes());
             GZIPInputStream gzis = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzis, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[4096];
            int n;
            while ((n = isr.read(buffer)) != -1) {
                writeLimited(writer, buffer, n);
            }
            return writer.toString();
        }
    }

    // -------------------- deflate --------------------
    private static String decompressDeflate(Buffer compressed) throws IOException {
        byte[] bytes = compressed.getBytes();
        try {
            return inflate(bytes, false); // zlib 包裹
        } catch (IOException e) {
            return inflate(bytes, true); // 裸 deflate
        }
    }

    private static String inflate(byte[] data, boolean nowrap) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             InflaterInputStream iis = new InflaterInputStream(bais, new Inflater(nowrap));
             InputStreamReader isr = new InputStreamReader(iis, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[4096];
            int n;
            while ((n = isr.read(buffer)) != -1) {
                writeLimited(writer, buffer, n);
            }
            return writer.toString();
        }
    }

    // -------------------- Brotli --------------------
    private static String decompressBrotli(Buffer compressed) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed.getBytes());
             BrotliInputStream bis = new BrotliInputStream(bais);
             InputStreamReader isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[4096];
            int n;
            while ((n = isr.read(buffer)) != -1) {
                writeLimited(writer, buffer, n);
            }
            return writer.toString();
        }
    }

    // -------------------- Zstandard --------------------
    private static String decompressZstd(Buffer compressed) {
       throw new UnsupportedOperationException("Zstandard");
    }
}
