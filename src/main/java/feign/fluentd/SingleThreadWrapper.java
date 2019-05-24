package feign.fluentd;

import org.fluentd.logger.FluentLogger;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SingleThreadWrapper extends FluentLogger {
    private final ExecutorService executorService;
    private final FluentLogger realLogger;

    public SingleThreadWrapper(FluentLogger realLogger) {
        this.executorService = Executors.newSingleThreadExecutor();
        this.realLogger = realLogger;
    }

    @Override
    public boolean log(String tag, String key, Object value) {
        return super.log(tag, key, value);
    }

    @Override
    public boolean log(String tag, String key, Object value, long timestamp) {
        return super.log(tag, key, value, timestamp);
    }

    @Override
    public boolean log(String tag, Map<String, Object> data) {
        return super.log(tag, data);
    }

    @Override
    public boolean log(String tag, Map<String, Object> data, long timestamp) {
        this.executorService.submit(() -> {
            try {
                realLogger.log(tag, data, timestamp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return true;
    }
}
