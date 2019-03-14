package feign.fluentd;

import feign.*;
import feign.Request.HttpMethod;
import org.fluentd.logger.FluentLogger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class FluentdLoggerTest {
    private static final Request REQUEST =
            new RequestTemplate().method(HttpMethod.POST.toString())
                    .append("http://api.example.com")
                    .resolve(Collections.emptyMap())
                    .header("test", "111", "222")
                    .body("some body")
                    .request();
    private static final Response RESPONSE =
            Response.builder()
                    .status(200)
                    .reason("OK")
                    .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
                    .headers(new HashMap<String, Collection<String>>() {{
                        put("test", asList("aaa"));
                    }})
                    .body("some body", Charset.forName("utf-8"))
                    .build();
    private static final String CONFIG_KEY = "client#method()";
    private static final HashMap<String, String> META_MAP = new HashMap<String, String>() {{
        put("client", "client");
        put("method", "method");
    }};

    @Mock
    private FluentLogger trueLogger;

    @Captor
    ArgumentCaptor<Map<String, Object>> captor;

    private FluentdLogger fluentdLogger;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        fluentdLogger = new FluentdLogger(trueLogger);
    }

    @Test
    public void should_LogRequest_full() {
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.FULL, REQUEST);
        verify(trueLogger).log(eq("feign"), eq("request"), captor.capture());
        final Map<String, Object> request = captor.getValue();
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(((Map<String, Object>) request.get("headers")).get("test"), is(asList("111", "222")));
        assertThat(request.get("body"), is("some body"));
        assertThat(request.get("body-bytes"), is(9));
        assertThat(request.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogRequest_headers_only() {
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.HEADERS, REQUEST);
        verify(trueLogger).log(eq("feign"), eq("request"), captor.capture());
        final Map<String, Object> request = captor.getValue();
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(((Map<String, Object>) request.get("headers")).get("test"), is(asList("111", "222")));
        assertThat(request.get("body"), nullValue());
        assertThat(request.get("body-bytes"), is(9));
        assertThat(request.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogRequest_basics() {
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
        verify(trueLogger).log(eq("feign"), eq("request"), captor.capture());
        final Map<String, Object> request = captor.getValue();
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(request.get("header"), nullValue());
        assertThat(request.get("body"), nullValue());
        assertThat(request.get("body-bytes"), nullValue());
        assertThat(request.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogRetry() {
        fluentdLogger.logRetry(CONFIG_KEY, Logger.Level.FULL);
        verify(trueLogger).log(eq("feign"), eq("retry"), captor.capture());
        assertThat(captor.getValue().get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogAndRebufferResponse_full() throws IOException {
        fluentdLogger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.FULL, RESPONSE, 1000L);
        verify(trueLogger).log(eq("feign"), eq("response"), captor.capture());
        final Map<String, Object> response = captor.getValue();
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000l));
        assertThat(((Map<String, Object>) response.get("headers")).get("test"), is(asList("aaa")));
        assertThat(response.get("body"), is("some body"));
        assertThat(response.get("body-bytes"), is(9));
        assertThat(response.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogAndRebufferResponse_headers() throws IOException {
        fluentdLogger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, RESPONSE, 1000L);
        verify(trueLogger).log(eq("feign"), eq("response"), captor.capture());
        final Map<String, Object> response = captor.getValue();
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000l));
        assertThat(((Map<String, Object>) response.get("headers")).get("test"), is(asList("aaa")));
        assertThat(response.get("body"), nullValue());
        assertThat(response.get("body-bytes"), is(9));
        assertThat(response.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogAndRebufferResponse_basic() throws IOException {
        fluentdLogger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 1000L);
        verify(trueLogger).log(eq("feign"), eq("response"), captor.capture());
        final Map<String, Object> response = captor.getValue();
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000L));
        assertThat(response.get("header"), nullValue());
        assertThat(response.get("body"), nullValue());
        assertThat(response.get("body-bytes"), nullValue());
        assertThat(response.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogIOException_full() {
        fluentdLogger.logIOException(CONFIG_KEY, Logger.Level.FULL, new IOException("test"), 1000L);
        verify(trueLogger).log(eq("feign"), eq("io_exception"), captor.capture());
        final Map<String, Object> exception = captor.getValue();
        assertThat(exception.get("name"), is("IOException"));
        assertThat(exception.get("message"), is("test"));
        assertThat(exception.get("details"), notNullValue());
        assertThat(exception.get("elapsedTimeMs"), is(1000L));
        assertThat(exception.get("meta"), is(META_MAP));
    }

    @Test
    public void should_LogIOException_non_full() {
        fluentdLogger.logIOException(CONFIG_KEY, Logger.Level.HEADERS, new IOException("test"), 1000L);
        verify(trueLogger).log(eq("feign"), eq("io_exception"), captor.capture());
        final Map<String, Object> exception = captor.getValue();
        assertThat(exception.get("name"), is("IOException"));
        assertThat(exception.get("message"), is("test"));
        assertThat(exception.get("details"), nullValue());
        assertThat(exception.get("elapsedTimeMs"), is(1000L));
        assertThat(exception.get("meta"), is(META_MAP));
    }
}