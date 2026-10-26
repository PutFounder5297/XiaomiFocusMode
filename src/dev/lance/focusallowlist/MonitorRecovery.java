package dev.lance.focusallowlist;

/** Bounded service recovery. A successful start must not reset the crash-loop budget. */
final class MonitorRecovery {
    private long sessionStart;
    private long sessionDuration;
    private long window;
    private long next;
    private int attempts;
    private boolean initialized;

    boolean request(long start, long duration, long elapsed, boolean live) {
        if (start <= 0L || duration <= 0L) return false;
        if (!initialized || sessionStart != start || sessionDuration != duration) {
            initialized = true;
            sessionStart = start;
            sessionDuration = duration;
            window = elapsed;
            next = elapsed;
            attempts = 0;
        }
        if (elapsed < window || elapsed - window >= 60000L) {
            window = elapsed;
            attempts = 0;
            next = elapsed;
        }
        if (live || attempts >= 3 || elapsed < next) return false;
        attempts++;
        next = elapsed + (attempts == 1 ? 5000L : 15000L);
        return true;
    }
    void clear() { initialized = false; attempts = 0; }
}
