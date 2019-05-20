package feign.fluentd;

import feign.*;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class SerializedFluentdLoggerTest {
    private static final Request REQUEST =
            new RequestTemplate().method(Request.HttpMethod.POST.toString())
                    .append("http://api.example.com")
                    .resolve(Collections.emptyMap())
                    .header("test", "111", "222")
                    .body("some body")
                    .request();
    private static final Response RESPONSE =
            Response.builder()
                    .status(200)
                    .reason("OK")
                    .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
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

    private SerializedFluentdLogger fluentdLogger;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        fluentdLogger = new SerializedFluentdLogger(trueLogger);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_log_as_whole() throws IOException {
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.FULL, REQUEST);
        fluentdLogger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.FULL, RESPONSE, 100L);
        verify(trueLogger).log(eq("feign"), captor.capture(), anyLong());
        final Map<String, Object> value = captor.getValue();
        assertThat(value, notNullValue());
        final Map<String, Object> request = (Map<String, Object>) value.get("request");
        assertThat(request.get("url"), is("http://api.example.com"));
        final Map<String, Object> response = (Map<String, Object>) value.get("response");
        assertThat(response.get("elapsedTimeMs"), is(100L));
        assertThat(value.get("meta_data"), is(META_MAP));
    }

    @Test
    public void should_log_ex_with_request() {
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.FULL, REQUEST);
        fluentdLogger.logIOException(CONFIG_KEY, Logger.Level.FULL, new IOException("test"), 100L);
        verify(trueLogger).log(eq("feign"), captor.capture(), anyLong());
        final Map<String, Object> value = captor.getValue();
        assertThat(value, notNullValue());
        final Map<String, Object> request = (Map<String, Object>) value.get("request");
        assertThat(request.get("url"), is("http://api.example.com"));
        final Map<String, Object> exception = (Map<String, Object>) value.get("io_exception");
        assertThat(exception.get("elapsedTimeMs"), is(100L));
        assertThat(value.get("meta_data"), is(META_MAP));
    }

    @Test
    public void should_log_retry_with_times() {
        fluentdLogger.logRetry(CONFIG_KEY, Logger.Level.FULL);
        fluentdLogger.logRetry(CONFIG_KEY, Logger.Level.FULL);
        fluentdLogger.logIOException(CONFIG_KEY, Logger.Level.FULL, new IOException(""), 111L);
        verify(trueLogger).log(eq("feign"), captor.capture(), anyLong());
        final Map<String, Object> value = captor.getValue();
        assertThat(value, notNullValue());
        final Map<String, Object> retry = (Map<String, Object>) value.get("retry");
        assertThat(retry.size(), is(2));
        assertThat(retry.get("0"), notNullValue());
        assertThat(retry.get("1"), notNullValue());
    }

    @Test
    public void should_log_dirty_context() {
        fluentdLogger.logRetry(CONFIG_KEY, Logger.Level.FULL);
        fluentdLogger.logRetry(CONFIG_KEY, Logger.Level.FULL);
        fluentdLogger.logRequest(CONFIG_KEY, Logger.Level.FULL, REQUEST);
        fluentdLogger.logIOException(CONFIG_KEY, Logger.Level.FULL, new IOException(""), 111L);
        verify(trueLogger).log(eq("feign"), captor.capture(), anyLong());
        final Map<String, Object> value = captor.getValue();
        assertThat(value, notNullValue());
        final Map<String, Object> dirty = (Map<String, Object>) value.get("dirty_context");
        assertThat(dirty, notNullValue());
        assertThat(dirty.get("retry"), notNullValue());
    }
}
