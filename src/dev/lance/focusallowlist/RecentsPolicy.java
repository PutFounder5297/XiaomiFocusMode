package dev.lance.focusallowlist;

/** Exact native pages only; never hide unrelated settings or app-lock tasks by prefix. */
final class RecentsPolicy {
    static boolean focusPage(String pkg, String name) {
        if (!Policy.TARGET.equals(pkg) || name == null) return false;
        return name.equals("com.xiaomi.misettings.usagestats.focusmode.port.FocusModePortActivity")
            || name.equals("com.xiaomi.misettings.usagestats.focusmode.land.FocusModeLandActivity")
            || name.equals("com.xiaomi.misettings.usagestats.focusmode.port.FocusModeTimingPortActivity")
            || name.equals("com.xiaomi.misettings.usagestats.focusmode.land.FocusModeTimingLandActivity");
    }

    static int taskId(int modern, int persistent, int legacy) {
        if (modern >= 0) return modern;
        if (persistent >= 0) return persistent;
        return legacy;
    }

    static boolean hide(int current, int candidate, String basePkg, String baseName,
                        String topPkg, String topName) {
        return (current >= 0 && candidate == current)
            || focusPage(basePkg, baseName) || focusPage(topPkg, topName);
    }
    private RecentsPolicy() {}
}
