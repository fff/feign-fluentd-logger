package feign.fluentd;

public enum LogType {
    request(false), response(true), retry(false), io_exception(true), meta_data(false), dirty_context(false);
    public final boolean isFinalStep;

    LogType(boolean isFinalStep) {
        this.isFinalStep = isFinalStep;
    }
}
