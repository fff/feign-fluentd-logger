package feign.fluentd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import java.util.Collections;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class FluentdLoggerTest {
    private static final String CONFIG_KEY = "someMethod()";
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
                    .headers(ImmutableMap.of("test", ImmutableList.of("aaa")))
                    .body("some body", Charset.forName("utf-8"))
                    .build();

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
        fluentdLogger.logRequest("test()", Logger.Level.FULL, REQUEST);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> request = (Map<String, Object>) captor.getValue().get("request");
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(((Map<String, Object>) request.get("header")).get("test"), is(asList("111", "222")));
        assertThat(request.get("body"), is("some body"));
        assertThat(request.get("body-bytes"), is(9));
    }

    @Test
    public void should_LogRequest_headers_only() {
        fluentdLogger.logRequest("test()", Logger.Level.HEADERS, REQUEST);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> request = (Map<String, Object>) captor.getValue().get("request");
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(((Map<String, Object>) request.get("header")).get("test"), is(asList("111", "222")));
        assertThat(request.get("body"), nullValue());
        assertThat(request.get("body-bytes"), is(9));
    }

    @Test
    public void should_LogRequest_basics() {
        fluentdLogger.logRequest("test()", Logger.Level.BASIC, REQUEST);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> request = (Map<String, Object>) captor.getValue().get("request");
        assertThat(request, notNullValue());
        assertThat(request.get("url"), is("http://api.example.com"));
        assertThat(request.get("method"), is("POST"));
        assertThat(request.get("header"), nullValue());
        assertThat(request.get("body"), nullValue());
        assertThat(request.get("body-bytes"), nullValue());
    }

    @Test
    public void should_LogRetry() {
        fluentdLogger.logRetry("test()", Logger.Level.FULL);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> retry = (Map<String, Object>) captor.getValue().get("retry");
        assertThat(retry.get("key"), is("test()"));
    }

    @Test
    public void should_LogAndRebufferResponse_full() throws IOException {
        fluentdLogger.logAndRebufferResponse("test()", Logger.Level.FULL, RESPONSE, 1000L);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> response = (Map<String, Object>) captor.getValue().get("response");
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000l));
        assertThat(((Map<String, Object>) response.get("header")).get("test"), is(asList("aaa")));
        assertThat(response.get("body"), is("some body"));
        assertThat(response.get("body-bytes"), is(9));
    }

    @Test
    public void should_LogAndRebufferResponse_headers() throws IOException {
        fluentdLogger.logAndRebufferResponse("test()", Logger.Level.HEADERS, RESPONSE, 1000L);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> response = (Map<String, Object>) captor.getValue().get("response");
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000l));
        assertThat(((Map<String, Object>) response.get("header")).get("test"), is(asList("aaa")));
        assertThat(response.get("body"), nullValue());
        assertThat(response.get("body-bytes"), is(9));
    }

    @Test
    public void should_LogAndRebufferResponse_basic() throws IOException {
        fluentdLogger.logAndRebufferResponse("test()", Logger.Level.BASIC, RESPONSE, 1000L);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> response = (Map<String, Object>) captor.getValue().get("response");
        assertThat(response.get("status"), is(200));
        assertThat(response.get("reason"), is(" OK"));
        assertThat(response.get("elapsedTimeMs"), is(1000L));
        assertThat(response.get("header"), nullValue());
        assertThat(response.get("body"), nullValue());
        assertThat(response.get("body-bytes"), nullValue());
    }

    @Test
    public void should_LogIOException_full() {
        fluentdLogger.logIOException("test()", Logger.Level.FULL, new IOException("test"), 1000L);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> exception = (Map<String, Object>) captor.getValue().get("exception");
        assertThat(exception.get("name"), is("IOException"));
        assertThat(exception.get("message"), is("test"));
        assertThat(exception.get("detail"), notNullValue());
        assertThat(exception.get("elapsedTimeMs"), is(1000L));
    }

    @Test
    public void should_LogIOException_non_full() {
        fluentdLogger.logIOException("test()", Logger.Level.HEADERS, new IOException("test"), 1000L);
        verify(trueLogger).log(eq("test"), captor.capture());
        final Map<String, Object> exception = (Map<String, Object>) captor.getValue().get("exception");
        assertThat(exception.get("name"), is("IOException"));
        assertThat(exception.get("message"), is("test"));
        assertThat(exception.get("detail"), nullValue());
        assertThat(exception.get("elapsedTimeMs"), is(1000L));
    }
}