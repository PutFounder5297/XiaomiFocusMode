package dev.lance.focusallowlist;

/** Whole minutes, with a conservative signed-int millisecond budget for native consumers. */
final class DurationPolicy {
    static final int MAX = Integer.MAX_VALUE / 60000;
    static boolean valid(int minutes) { return minutes >= 1 && minutes <= MAX; }
    static int parse(CharSequence input) {
        if (input == null || input.length() == 0 || input.length() > 5) return 0;
        int minutes = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < '0' || c > '9') return 0;
            minutes = minutes * 10 + c - '0';
        }
        return valid(minutes) ? minutes : 0;
    }
    private DurationPolicy() {}
}
