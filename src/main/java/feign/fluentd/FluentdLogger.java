package feign.fluentd;

import com.google.common.collect.ImmutableMap;
import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import org.fluentd.logger.FluentLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static feign.Util.UTF_8;
import static feign.Util.decodeOrDefault;

public class FluentdLogger extends Logger {

    private final FluentLogger logger;

    public FluentdLogger(FluentLogger logger) {
        assert logger != null;
        this.logger = logger;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        // do nothing
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        ImmutableMap.Builder<String, Object> requestMap = ImmutableMap.builder();
        requestMap.put("method", request.httpMethod().toString());
        requestMap.put("url", request.url());
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            requestMap.put("header", request.headers());

            int bodyLength = 0;
            if (request.body() != null) {
                if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                    String bodyText =
                            request.charset() != null ? new String(request.body(), request.charset()) : null;
                    requestMap.put("body", bodyText != null ? bodyText : "Binary data");
                }
                bodyLength = request.body().length;
            }
            requestMap.put("body-bytes", bodyLength);
        }
        doLog(configKey, "request", requestMap.build());
    }

    private void doLog(String configKey, String paramPrefix, Map<String, Object> paramValues) {
        logger.log(configKey.substring(0, configKey.indexOf('(')), ImmutableMap.of(paramPrefix, paramValues));
    }

    @Override
    protected void logRetry(String configKey, Level logLevel) {
        doLog(configKey, "retry", ImmutableMap.of("key", configKey));
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        String reason =
                response.reason() != null && logLevel.compareTo(Level.NONE) > 0 ? " " + response.reason()
                        : "";
        int status = response.status();

        final ImmutableMap.Builder<String, Object> responseMap = ImmutableMap.builder();
        responseMap.put("status", status).put("reason", reason).put("elapsedTimeMs", elapsedTime);
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

            responseMap.put("header", response.headers());

            int bodyLength = 0;
            if (response.body() != null && !(status == 204 || status == 205)) {
                // HTTP 204 No Content "...response MUST NOT include a message-body"
                // HTTP 205 Reset Content "...response MUST NOT include an entity"
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                bodyLength = bodyData.length;
                if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0) {
                    responseMap.put("body", decodeOrDefault(bodyData, UTF_8, "Binary data"));
                }
                responseMap.put("body-bytes", bodyLength);
                doLog(configKey, "response", responseMap.build());
                return response.toBuilder().body(bodyData).build();
            } else {
                responseMap.put("body-bytes", bodyLength);
            }
        }
        doLog(configKey, "response", responseMap.build());
        return response;
    }

    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        final ImmutableMap.Builder<String, Object> exMap = ImmutableMap.builder();
        exMap.put("name", ioe.getClass().getSimpleName()).put("message", ioe.getMessage()).put("elapsedTimeMs", elapsedTime);
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            exMap.put("detail", sw.toString());
        }
        doLog(configKey, "exception", exMap.build());
        return ioe;
    }
}
