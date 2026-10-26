package dev.lance.focusallowlist;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** A small, buttons-free template. Inputs are numbers; all other JSON text is fixed. */
final class IslandTemplate {
    static final String PIC = "miui.focus.pic_focusallowlist_v2";
    static final String REVISION_KEY = "dev.lance.focusallowlist.template_revision";
    static final String REVISION = "buttons-free-v2";
    private static final long MAX_DURATION = 12L * 60L * 60L * 1000L;

    static String build(int protocol, long remaining) {
        // Only the device's confirmed protocol is enabled; unknown versions use the normal notification.
        if (protocol != 3 || remaining <= 0L || remaining > MAX_DURATION) return null;
        String digits = IslandTiming.label(remaining);
        // No user text is interpolated. digits contains only decimal digits and a colon.
        // Static digits avoid the undocumented timerWhen/timerTotal interpretation.
        String json = String.format(Locale.ROOT,
            "{\"param_v2\":{"
            + "\"protocol\":1,\"business\":\"focus_study\","
            + "\"updatable\":true,\"reopen\":\"reopen\","
            + "\"islandFirstFloat\":false,\"enableFloat\":false,\"filterWhenNoPermission\":false,"
            + "\"timeout\":%d,"
            + "\"ticker\":\"专注 %s\",\"tickerPic\":\"%s\","
            + "\"aodTitle\":\"专注 %s\",\"aodPic\":\"%s\","
            + "\"baseInfo\":{\"type\":1,\"title\":\"剩余 %s\",\"content\":\"点击返回当前专注\"},"
            + "\"picInfo\":{\"type\":1,\"pic\":\"%s\",\"picDark\":\"%s\"},"
            + "\"param_island\":{\"islandProperty\":1,\"islandOrder\":false,\"islandTimeout\":%d,"
            + "\"bigIslandArea\":{"
            + "\"imageTextInfoLeft\":{\"type\":1,\"picInfo\":{\"type\":1,\"pic\":\"%s\"},"
            + "\"textInfo\":{\"title\":\"专注\",\"showHighlightColor\":false}},"
            + "\"sameWidthDigitInfo\":{\"digit\":\"%s\",\"showHighlightColor\":false}},"
            + "\"smallIslandArea\":{\"picInfo\":{\"type\":1,\"pic\":\"%s\"}}}}}",
            IslandTiming.timeout(remaining, 60000L, 1), digits, PIC, digits, PIC,
            digits, PIC, PIC, IslandTiming.timeout(remaining, 1000L, 0), PIC, digits, PIC);
        return json.getBytes(StandardCharsets.UTF_8).length <= 3072 ? json : null;
    }

    static long refreshDelay(long remaining, boolean island) {
        return Math.max(250L, Math.min(island ? 1000L : 30000L, remaining));
    }
    private IslandTemplate() {}
}
