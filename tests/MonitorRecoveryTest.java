package dev.lance.focusallowlist;

public final class MonitorRecoveryTest {
    private static int count;
    private static void check(boolean value) { count++; if (!value) throw new AssertionError(count); }
    public static void main(String[] args) {
        MonitorRecovery r = new MonitorRecovery();
        check(!r.request(0, 60000, 0, false));
        check(!r.request(1, 0, 0, false));
        check(r.request(1, 60000, 100, false));
        check(!r.request(1, 60000, 100, false)); // repeated destroy/page callbacks
        check(!r.request(1, 60000, 5099, false));
        check(!r.request(1, 60000, 5100, true)); // alive, do not repeatedly start
        check(r.request(1, 60000, 5100, false));
        check(!r.request(1, 60000, 20099, false));
        check(r.request(1, 60000, 20100, false));
        check(!r.request(1, 60000, 59999, false)); // three fast failures exhaust budget
        check(!r.request(1, 60000, 60099, false));
        check(r.request(1, 60000, 60100, false)); // one new window
        check(r.request(2, 60000, 60200, false)); // new native session, not old queued identity
        check(!r.request(2, 60000, 60201, false));
        r.clear();
        check(r.request(2, 60000, 60202, false));
        check(r.request(2, 60000, 0, false)); // monotonic clock rollback defensive
        check(r.request(2, 120000, 1, false));
        check(!r.request(2, 120000, 2, true));
        System.out.println("MonitorRecoveryTest: " + count + " checks passed");
    }
}
