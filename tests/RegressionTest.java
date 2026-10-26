package dev.lance.focusallowlist;

public final class RegressionTest {
    private static int checks;
    private static final StackTraceElement[] NONE = new StackTraceElement[0];
    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
    private static StackTraceElement[] frame(String name, String method) {
        return new StackTraceElement[]{new StackTraceElement(name, method, "source.java", 1)};
    }
    public static void main(String[] args) throws Exception {
        final FocusScope scope = new FocusScope();
        check(!scope.contains(frame("be.b", "run")), "merged class alone is not focus");
        check(!scope.contains(frame("be.c", "run")), "merged executor alone is not focus");
        check(!scope.contains(frame("com.xiaomi.misettings.password.appcontrol.ui.AccessCheckActivity", "onCreate")), "password entry is not focus origin");
        check(scope.contains(frame("be.e", "r")), "native effects restoration is focus");
        check(scope.contains(frame("com.xiaomi.misettings.usagestats.focusmode.FocusModeActivityBase", "onCreate")), "native focus activity");
        for (int id = 0; id <= 6; id++) check(FocusScope.task("be.b", id), "focus runnable " + id);
        check(!FocusScope.task("be.b", 7), "keyguard task remains native");
        check(!FocusScope.task("be.b", 8), "resource preloading remains native");
        check(FocusScope.task("be.c", 0), "forced finish is focus");
        for (int id = 1; id <= 28; id++) check(!FocusScope.task("be.c", id), "unrelated merged task " + id);
        int outer = scope.enter();
        int inner = scope.enter();
        scope.restore(inner);
        check(scope.contains(NONE), "nested callback keeps outer origin");
        final boolean[] other = {true};
        Thread worker = new Thread(new Runnable() { public void run() { other[0] = scope.contains(NONE); } });
        worker.start(); worker.join();
        check(!other[0], "origin does not leak into another thread");
        scope.restore(outer);
        check(!scope.contains(NONE), "callback completion clears origin");
        int before = scope.enter();
        try { throw new IllegalStateException("native task failure"); }
        catch (IllegalStateException expected) {}
        finally { scope.restore(before); }
        check(!scope.contains(NONE), "failed task leaves origin clean");

        WatchdogRegistry<Object> timers = new WatchdogRegistry<Object>();
        Object service = new Object();
        Runnable a = new Runnable() { public void run() {} };
        Runnable b = new Runnable() { public void run() {} };
        check(!timers.install(service, a), "late callback cannot register service");
        timers.register(service);
        check(timers.install(service, a), "live service schedules first timer");
        check(!timers.install(service, b), "foreground changes cannot reset or duplicate timer");
        check(timers.current(service, a), "current timer can execute");
        check(!timers.current(service, b), "obsolete timer cannot execute");
        check(timers.finish(service, a), "finish request begins once");
        check(!timers.finish(service, a), "duplicate finish blocked");
        check(!timers.current(service, a), "pending finish stops ticks");
        check(timers.retry(service, a) && timers.current(service, a), "transient finish failure retries");
        check(timers.unregister(service) == a, "destroy returns timer for cancellation");
        check(!timers.retry(service, a), "failure after destroy cannot retry");
        check(!timers.install(service, b), "late foreground event cannot resurrect timer");
        timers.register(service);
        check(timers.install(service, b), "new lifecycle has new timer");
        check(!timers.current(service, a) && !timers.retry(service, a), "old lifecycle cannot finish new focus");

        long start = 1700000000000L;
        check(FocusTiming.delay(start, 1, start) == 60000L, "one minute trial");
        check(FocusTiming.delay(start, 1, start + 59000L) == 1250L, "deadline approaches");
        check(FocusTiming.delay(start, 1, start + 60000L) == 250L, "deadline reached");
        check(FocusTiming.delay(start, 1, start + 61000L) == 250L, "overdue tick is bounded");
        check(FocusTiming.delay(start, 20, start) == 60000L, "long session polls at most once per minute");
        check(FocusTiming.delay(start, 20, start - 60000L) == 60000L, "clock rollback retains native wall clock");
        check(FocusTiming.delay(start, Integer.MAX_VALUE, start) == 60000L, "duration multiplication does not overflow int");
        check(IslandTiming.end(start, 60000L) == start + 60000L, "display deadline matches native one-minute trial");
        check(IslandTiming.remaining(1, start, 60000L, start + 59000L) == 1000L, "returning to display retains original deadline");
        check(IslandTiming.remaining(0, start, 60000L, start) == 0L, "ended focus removes display");
        check(IslandTiming.remaining(1, start, 60000L, start + 60000L) == 0L, "no negative countdown at expiry");
        check(IslandTiming.remaining(1, start, 60000L, start + 61000L) == 0L, "no stale display after expiry");
        check(IslandTiming.remaining(1, 0L, 60000L, start) == 0L, "missing start does not invent session");
        check(IslandTiming.remaining(1, start, 0L, start) == 0L, "missing duration does not invent session");
        check(IslandTiming.end(Long.MAX_VALUE - 10L, 60000L) == 0L, "invalid deadline overflow rejected");
        check(IslandTiming.remaining(1, start, 60000L, start - 1000L) == 61000L, "display follows native wall clock after adjustment");
        check(IslandTiming.label(60000L).equals("01:00"), "minute trial label");
        check(IslandTiming.label(1L).equals("00:01"), "last fraction does not show premature zero");
        check(IslandTiming.label(7200000L).equals("120:00"), "long reading session retains all minutes");
        check(IslandTiming.label(-1000L).equals("00:00"), "negative display input stays at zero");
        check(IslandTiming.timeout(60000L, 1000L, 0) == 60, "trial island deadline is seconds");
        check(IslandTiming.timeout(1L, 1000L, 0) == 1, "partial last second does not use zero timeout");
        check(IslandTiming.timeout(60000L, 60000L, 1) == 2, "minute metadata includes one minute grace");
        check(IslandTiming.timeout(0L, 60000L, 1) == 0, "no live timeout for an expired display");
        check(IslandTiming.timeout(Long.MAX_VALUE, 1000L, 0) == Integer.MAX_VALUE, "seconds never overflow after rounding");
        check(IslandTiming.timeout(Long.MAX_VALUE, 60000L, 1) == Integer.MAX_VALUE, "minutes never overflow before grace");
        check(IslandTiming.timeout(Integer.MAX_VALUE - 1L, 1L, 1) == Integer.MAX_VALUE, "maximum integer grace boundary");
        check(IslandTiming.timeout(2147483648L, 1L, 0) == Integer.MAX_VALUE, "large timeout does not wrap negative");
        System.out.println("PASS " + checks + " origin, lifecycle, retry and timing regression checks");
    }
}
