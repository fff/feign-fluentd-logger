# feign-fluentd
fluentd extension for feign logger

# KNOWN ISSUE
(fluent-logger-java)[https://github.com/fluent/fluent-logger-java] is not thread-safe for default 'RawSocketSender', should be designed carefully for multi-thread context;
