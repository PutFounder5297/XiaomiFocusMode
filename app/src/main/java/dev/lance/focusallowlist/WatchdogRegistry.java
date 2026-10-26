package dev.lance.focusallowlist;

import java.util.Map;
import java.util.WeakHashMap;

/** Only onCreate registers a service: late foreground callbacks cannot resurrect a destroyed timer. */
final class WatchdogRegistry<S> {
    private static final class Entry { Runnable tick; boolean finishing; }
    private final Map<S, Entry> entries = new WeakHashMap<S, Entry>();
    synchronized void register(S service) { entries.put(service, new Entry()); }
    synchronized Runnable unregister(S service) {
        Entry old = entries.remove(service);
        return old == null ? null : old.tick;
    }
    synchronized boolean install(S service, Runnable tick) {
        Entry entry = entries.get(service);
        if (entry == null || entry.tick != null) return false;
        entry.tick = tick;
        return true;
    }
    synchronized boolean current(S service, Runnable tick) {
        Entry entry = entries.get(service);
        return entry != null && entry.tick == tick && !entry.finishing;
    }
    synchronized boolean finish(S service, Runnable tick) {
        if (!current(service, tick)) return false;
        entries.get(service).finishing = true;
        return true;
    }
    synchronized boolean retry(S service, Runnable tick) {
        Entry entry = entries.get(service);
        if (entry == null || entry.tick != tick) return false;
        entry.finishing = false;
        return true;
    }
}
