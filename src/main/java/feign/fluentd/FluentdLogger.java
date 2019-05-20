package feign.fluentd;

import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import org.fluentd.logger.FluentLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static feign.Util.UTF_8;
import static feign.Util.decodeOrDefault;

public class FluentdLogger extends Logger {

    protected final FluentLogger logger;
    protected final String tagPrefix;

    public FluentdLogger(FluentLogger logger) {
        this(logger, "feign");
    }

    public FluentdLogger(FluentLogger logger, String tagPrefix) {
        assert logger != null;
        this.logger = logger;
        this.tagPrefix = tagPrefix;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        // do nothing
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", request.httpMethod().toString());
        requestMap.put("url", request.url());
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            requestMap.put("headers", request.headers());

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
        doLog(configKey, LogType.request, requestMap);
    }

    protected void doLog(String configKey, LogType paramPrefix, Map<String, Object> paramValues) {
        paramValues.put("meta", constructMetaMap(configKey));
        logger.log(tagPrefix, paramPrefix.name(), paramValues, System.currentTimeMillis());
    }

    Map<String, Object> constructMetaMap(String configKey) {
        final String[] clientAndMethod = configKey.substring(0, configKey.indexOf('(')).split("#");
        final Map<String, Object> metaMap = new HashMap<>();
        if (clientAndMethod.length > 1) {
            metaMap.put("client", clientAndMethod[0]);
            metaMap.put("method", clientAndMethod[1]);
        } else {
            metaMap.put("method", clientAndMethod[0]);
        }
        return metaMap;
    }

    @Override
    protected void logRetry(String configKey, Level logLevel) {
        doLog(configKey, LogType.retry, new HashMap<>());
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        String reason =
                response.reason() != null && logLevel.compareTo(Level.NONE) > 0 ? " " + response.reason()
                        : "";
        int status = response.status();

        final Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("status", status);
        responseMap.put("reason", reason);
        responseMap.put("elapsedTimeMs", elapsedTime);
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

            responseMap.put("headers", response.headers());

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
                doLog(configKey, LogType.response, responseMap);
                return response.toBuilder().body(bodyData).build();
            } else {
                responseMap.put("body-bytes", bodyLength);
            }
        }
        doLog(configKey, LogType.response, responseMap);
        return response;
    }

    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        final Map<String, Object> exMap = new HashMap<>();
        exMap.put("name", ioe.getClass().getSimpleName());
        exMap.put("message", ioe.getMessage());
        exMap.put("elapsedTimeMs", elapsedTime);
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            exMap.put("details", sw.toString());
        }
        doLog(configKey, LogType.io_exception, exMap);
        return ioe;
    }
}
