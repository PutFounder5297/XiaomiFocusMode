package dev.lance.focusallowlist;

/** One recoverable display loop. It owns no native session state or Android component. */
final class IslandRefreshLoop {
    interface Scheduler {
        boolean post(Runnable task, long delay);
        void remove(Runnable task);
    }
    interface Renderer {
        /** Read the native clock and render; nonpositive delay means no live session. */
        long refresh();
        void clear();
    }
    private final Scheduler scheduler;
    private final Renderer renderer;
    private Runnable tick;

    IslandRefreshLoop(Scheduler scheduler, Renderer renderer) {
        this.scheduler = scheduler;
        this.renderer = renderer;
    }
    synchronized boolean resume() {
        if (tick != null) scheduler.remove(tick);
        tick = new Runnable() {
            @Override public void run() {
                synchronized (IslandRefreshLoop.this) {
                    if (tick != this) return;
                    long delay = renderer.refresh();
                    // A renderer callback may have replaced or stopped this loop.
                    if (tick != this) return;
                    if (delay <= 0L || !scheduler.post(this, delay)) stop();
                }
            }
        };
        if (!scheduler.post(tick, 0L)) stop();
        return tick != null;
    }
    synchronized void stop() {
        Runnable old = tick;
        tick = null; // Invalidate already-dispatched callbacks before cancellation.
        if (old != null) scheduler.remove(old);
        renderer.clear();
    }
}
