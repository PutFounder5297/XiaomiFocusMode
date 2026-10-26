package dev.lance.focusallowlist;

final class FocusTiming {
    static long delay(long start, int minutes, long now) {
        long remaining = minutes * 60000L - (now - start);
        return Math.max(250L, Math.min(60000L, remaining + 250L));
    }
}
