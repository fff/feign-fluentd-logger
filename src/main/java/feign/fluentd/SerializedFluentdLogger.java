package feign.fluentd;

import org.fluentd.logger.FluentLogger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class SerializedFluentdLogger extends FluentdLogger {

    private ThreadLocal<Map<LogType, Map<String, Object>>> logQueue = ThreadLocal.withInitial(() -> new HashMap<>(4));

    public SerializedFluentdLogger(FluentLogger logger) {
        super(logger);
    }

    public SerializedFluentdLogger(FluentLogger logger, String tagPrefix) {
        super(logger, tagPrefix);
    }

    @Override
    protected void doLog(String configKey, LogType paramPrefix, Map<String, Object> paramValues) {
        final Map<LogType, Map<String, Object>> queueMap = logQueue.get();
        if (LogType.retry.equals(paramPrefix)) {
            final Map<String, Object> retryLogMap = queueMap.getOrDefault(LogType.retry, new HashMap<>(0));
            retryLogMap.put(String.valueOf(retryLogMap.size()), LocalDateTime.now());
            queueMap.put(LogType.retry, retryLogMap);
            return;
        } else if (LogType.request.equals(paramPrefix) && queueMap.size() > 0) {
            //context protection
            final Map<String, Object> dirtyContext = toStringMap(queueMap);
            queueMap.clear();
            queueMap.put(LogType.dirty_context, dirtyContext);
        }

        queueMap.put(paramPrefix, paramValues);

        if (paramPrefix.isFinalStep) {
            queueMap.put(LogType.meta_data, constructMetaMap(configKey));
            logger.log(tagPrefix, toStringMap(queueMap));
            logQueue.remove();
        }
    }

    private Map<String, Object> toStringMap(Map<LogType, Map<String, Object>> queueMap) {
        return queueMap.entrySet().stream().collect(toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }
}
