package dev.lance.focusallowlist;

/** Produce the exact production JSON for host parsing; never included in the APK. */
public final class IslandTemplateDump {
    public static void main(String[] args) {
        int protocol = Integer.parseInt(args[0]);
        for (int i = 1; i < args.length; i++) {
            long remaining = Long.parseLong(args[i]);
            String payload = IslandTemplate.build(protocol, remaining);
            boolean island = payload != null;
            System.out.println("{\"remaining\":" + remaining + ",\"delay\":"
                + IslandTemplate.refreshDelay(remaining, island) + ",\"payload\":"
                + (island ? payload : "null") + "}");
        }
    }
}
