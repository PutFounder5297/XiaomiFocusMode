package dev.lance.focusallowlist;

import java.util.ArrayList;

/** Runs the production scheduling logic with a lost queue and a persistent native clock. */
public final class IslandRefreshLoopTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static final class Queue implements IslandRefreshLoop.Scheduler {
        final ArrayList<Runnable> tasks = new ArrayList<Runnable>();
        final ArrayList<Long> delays = new ArrayList<Long>();
        boolean accept = true;
        public boolean post(Runnable task, long delay) {
            if (!accept) return false;
            tasks.add(task); delays.add(delay); return true;
        }
        public void remove(Runnable task) {
            for (int i = tasks.size() - 1; i >= 0; i--)
                if (tasks.get(i) == task) { tasks.remove(i); delays.remove(i); }
        }
        void runNext() { Runnable task = tasks.remove(0); delays.remove(0); task.run(); }
        void loseQueue() { tasks.clear(); delays.clear(); }
    }
    private static final class NativeClock implements IslandRefreshLoop.Renderer {
        int status = 1, updates, clears;
        long start = 1700000000000L, duration = 60000L, now = start, displayed;
        Runnable duringRefresh;
        public long refresh() {
            updates++;
            displayed = IslandTiming.remaining(status, start, duration, now);
            if (duringRefresh != null) { Runnable action = duringRefresh; duringRefresh = null; action.run(); }
            return displayed == 0L ? 0L : IslandTemplate.refreshDelay(displayed, true);
        }
        public void clear() { clears++; }
    }
    public static void main(String[] args) {
        Queue queue = new Queue();
        NativeClock clock = new NativeClock();
        IslandRefreshLoop loop = new IslandRefreshLoop(queue, clock);
        check(loop.resume(), "initial display schedules");
        check(queue.tasks.size() == 1 && queue.delays.get(0) == 0L, "resume requests immediate refresh");
        queue.runNext();
        check(clock.displayed == 60000L, "first display uses native remaining time");
        check(queue.tasks.size() == 1 && queue.delays.get(0) == 1000L, "one live island refresh chain");

        clock.now += 20000L;
        queue.loseQueue(); // Task cleanup can lose the callback without clearing native settings.
        check(loop.resume(), "page return recovers a lost callback");
        check(queue.tasks.size() == 1 && queue.delays.get(0) == 0L, "recovery does not wait for old delay");
        queue.runNext();
        check(clock.displayed == 40000L, "recovery catches up instead of restarting countdown");
        check(clock.start == 1700000000000L && clock.duration == 60000L, "native start and duration unchanged");

        Runnable old = queue.tasks.get(0);
        loop.resume(); loop.resume();
        int updates = clock.updates;
        old.run();
        check(clock.updates == updates, "already-dispatched obsolete callback cannot render");
        check(queue.tasks.size() == 1, "repeated lifecycle events do not create duplicate chains");
        queue.runNext();
        check(clock.clears == 0 && clock.displayed == 40000L, "recover while native focus active retains display");

        clock.now = clock.start + clock.duration;
        old = queue.tasks.get(0);
        queue.runNext();
        check(clock.displayed == 0L && clock.clears == 1, "expiry clears notification");
        check(queue.tasks.isEmpty(), "expiry stops callbacks");
        old.run();
        check(clock.clears == 1 && queue.tasks.isEmpty(), "expired callback cannot resurrect display");

        clock.start = clock.now; clock.duration = 300000L;
        loop.resume(); queue.runNext();
        updates = clock.updates; old.run();
        check(clock.updates == updates && clock.displayed == 300000L, "old session cannot affect new session");
        old = queue.tasks.get(0); loop.stop();
        check(queue.tasks.isEmpty(), "explicit display stop removes callbacks");
        updates = clock.updates; old.run();
        check(clock.updates == updates && queue.tasks.isEmpty(), "late callback after stop is inert");
        clock.status = 0; loop.resume(); queue.runNext();
        check(clock.displayed == 0L && queue.tasks.isEmpty(), "returning after native end leaves no island");

        // A fresh process has no old loop/owner but the system settings retain their deadline.
        Queue freshQueue = new Queue(); NativeClock freshClock = new NativeClock();
        freshClock.now += 45000L;
        IslandRefreshLoop fresh = new IslandRefreshLoop(freshQueue, freshClock);
        fresh.resume(); freshQueue.runNext();
        check(freshClock.displayed == 15000L, "process recreation reads persisted native clock");

        Queue rejected = new Queue(); NativeClock rejectedClock = new NativeClock();
        IslandRefreshLoop retry = new IslandRefreshLoop(rejected, rejectedClock);
        rejected.accept = false;
        check(!retry.resume(), "rejected initial post is reported");
        check(rejected.tasks.isEmpty() && rejectedClock.clears == 1, "rejected post does not leave frozen notification");
        rejected.accept = true; retry.resume(); rejected.accept = false; rejected.runNext();
        check(rejected.tasks.isEmpty() && rejectedClock.clears == 2, "rejected continuation also clears display");
        rejectedClock.now += 30000L; rejected.accept = true; retry.resume(); rejected.runNext();
        check(rejectedClock.displayed == 30000L, "later page return recovers a rejected post");

        final Queue reentrantQueue = new Queue(); final NativeClock reentrantClock = new NativeClock();
        final IslandRefreshLoop reentrant = new IslandRefreshLoop(reentrantQueue, reentrantClock);
        reentrantClock.duringRefresh = new Runnable() { public void run() { reentrant.resume(); } };
        reentrant.resume(); reentrantQueue.runNext();
        check(reentrantQueue.tasks.size() == 1 && reentrantQueue.delays.get(0) == 0L,
            "callback replacement during render leaves only new immediate tick");
        System.out.println("PASS " + checks + " production display recovery checks");
    }
}
