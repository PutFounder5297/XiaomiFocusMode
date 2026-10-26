package dev.lance.focusallowlist;

/** R8 merges unrelated tasks into be.b/be.c; class names alone are not focus provenance. */
final class FocusScope {
    private final ThreadLocal<Integer> depth = new ThreadLocal<Integer>();
    int enter() {
        Integer value = depth.get();
        int old = value == null ? 0 : value.intValue();
        depth.set(Integer.valueOf(old + 1));
        return old;
    }
    void restore(int old) {
        if (old == 0) depth.remove(); else depth.set(Integer.valueOf(old));
    }
    boolean contains(StackTraceElement[] frames) {
        Integer value = depth.get();
        if (value != null && value.intValue() > 0) return true;
        for (StackTraceElement frame : frames) {
            String name = frame.getClassName();
            if (name.startsWith("com.xiaomi.misettings.usagestats.focusmode.")
                || name.equals("be.e")
                || (name.equals("ae.b") && frame.getMethodName().equals("onForegroundWindowChanged"))) return true;
        }
        return false;
    }
    static boolean task(String name, int id) {
        return (name.equals("be.b") && id >= 0 && id <= 6) || (name.equals("be.c") && id == 0);
    }
}
