package dev.lance.focusallowlist;

/** Read-only display calculations; native focus remains responsible for the session. */
final class IslandTiming {
    static long end(long start, long duration) {
        if (start <= 0L || duration <= 0L || duration > Long.MAX_VALUE - start) return 0L;
        return start + duration;
    }
    static long remaining(int status, long start, long duration, long now) {
        long end = end(start, duration);
        if (status != 1 || end == 0L || now < 0L || now >= end) return 0L;
        return end - now;
    }
    static String label(long remaining) {
        remaining = Math.max(0L, remaining);
        long seconds = remaining / 1000L + (remaining % 1000L > 0L ? 1L : 0L);
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return (minutes < 10L ? "0" : "") + minutes + ":" + (rest < 10L ? "0" : "") + rest;
    }
    /** HyperOS timeout fields are int; avoid overflow before rounding or adding grace. */
    static int timeout(long remaining, long unit, int grace) {
        if (remaining <= 0L) return 0;
        if (unit <= 0L || grace < 0) throw new IllegalArgumentException("invalid timeout unit/grace");
        long units = remaining / unit + (remaining % unit > 0L ? 1L : 0L);
        return units >= Integer.MAX_VALUE - (long) grace ? Integer.MAX_VALUE : (int) units + grace;
    }
    private IslandTiming() {}
}
