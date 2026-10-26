package dev.lance.focusallowlist;

/** A journaled temporary lock. Exceptions never mean "unlocked" or clear ownership. */
final class ProcessLockLease {
    interface Backend {
        boolean locked() throws Exception;
        void lock(boolean value) throws Exception;
    }
    interface Journal {
        boolean pending();
        boolean record(boolean value);
    }
    static final int IDLE = 0, ACQUIRED = 1, KEPT = 2, RESTORED = 3;
    private final Backend backend;
    private final Journal journal;

    ProcessLockLease(Backend backend, Journal journal) {
        this.backend = backend;
        this.journal = journal;
    }
    int sync(boolean active) throws Exception {
        if (active) {
            if (backend.locked()) return KEPT; // A pre-existing user lock is never owned.
            // Commit BEFORE every write, including retries after process death or failed storage.
            if (!journal.record(true)) throw new Exception("lock journal commit failed");
            backend.lock(true);
            if (!backend.locked()) throw new Exception("native lock not applied");
            return ACQUIRED;
        }
        if (!journal.pending()) return IDLE;
        if (backend.locked()) backend.lock(false);
        if (backend.locked()) throw new Exception("native unlock not applied");
        if (!journal.record(false)) throw new Exception("restore journal commit failed");
        return RESTORED;
    }
}
