package dev.lance.focusallowlist;

public final class ProcessLockLeaseTest {
    private static int count;
    private static void check(boolean value) { count++; if (!value) throw new AssertionError(count); }
    private static final class State implements ProcessLockLease.Backend, ProcessLockLease.Journal {
        boolean locked, pending, commitFail, writeFail, writeThenFail, ignoreWrite;
        int writes, reads, failRead;
        String events = "";
        @Override public boolean locked() throws Exception {
            events += "R";
            if (++reads == failRead) throw new Exception("binder read failed");
            return locked;
        }
        @Override public void lock(boolean value) throws Exception {
            writes++;
            events += value ? "L" : "U";
            if (writeFail) throw new Exception("binder write failed");
            if (!ignoreWrite) locked = value;
            if (writeThenFail) throw new Exception("reply lost after mutation");
        }
        @Override public boolean pending() { return pending; }
        @Override public boolean record(boolean value) {
            events += value ? "J" : "C";
            if (commitFail) return false;
            pending = value;
            return true;
        }
        ProcessLockLease restart() { return new ProcessLockLease(this, this); }
    }
    private static void failed(ProcessLockLease lease, boolean active) {
        boolean threw = false;
        try { lease.sync(active); } catch (Exception expected) { threw = true; }
        check(threw);
    }
    public static void main(String[] args) throws Exception {
        State s = new State();
        ProcessLockLease l = s.restart();
        check(l.sync(false) == ProcessLockLease.IDLE && s.reads == 0 && s.writes == 0);
        check(l.sync(true) == ProcessLockLease.ACQUIRED && s.pending && s.locked);
        check(s.events.equals("RJLR")); // durable undo record precedes native mutation
        check(l.sync(true) == ProcessLockLease.KEPT && s.writes == 1);
        l = s.restart(); // survived process destruction using only persisted ownership
        check(l.sync(false) == ProcessLockLease.RESTORED && !s.locked && !s.pending);
        check(l.sync(false) == ProcessLockLease.IDLE && s.writes == 2);

        s = new State(); s.locked = true; l = s.restart();
        check(l.sync(true) == ProcessLockLease.KEPT && !s.pending && s.writes == 0);
        check(l.sync(false) == ProcessLockLease.IDLE && s.locked); // preserve pre-existing user lock
        s.locked = false;
        check(l.sync(true) == ProcessLockLease.ACQUIRED && s.pending);
        check(l.sync(false) == ProcessLockLease.RESTORED && !s.locked);

        s = new State(); s.failRead = 1; l = s.restart(); failed(l, true);
        check(!s.pending && s.writes == 0); // an unavailable service is not an unlocked package
        s = new State(); s.commitFail = true; l = s.restart(); failed(l, true);
        check(!s.locked && s.writes == 0);
        s.commitFail = false;
        check(l.sync(true) == ProcessLockLease.ACQUIRED);

        s = new State(); s.writeFail = true; l = s.restart(); failed(l, true);
        check(s.pending && !s.locked);
        s.writeFail = false;
        check(s.restart().sync(false) == ProcessLockLease.RESTORED && !s.pending);

        s = new State(); s.writeThenFail = true; l = s.restart(); failed(l, true);
        check(s.locked && s.pending); // uncertain write: retain undo ownership
        s.writeThenFail = false;
        check(s.restart().sync(false) == ProcessLockLease.RESTORED && !s.locked);

        s = new State(); s.failRead = 2; l = s.restart(); failed(l, true);
        check(s.locked && s.pending);
        check(s.restart().sync(false) == ProcessLockLease.RESTORED && !s.locked);

        s = new State(); l = s.restart(); l.sync(true); s.ignoreWrite = true; failed(l, false);
        check(s.pending && s.locked); // silent failure cannot discard restore journal
        s.ignoreWrite = false;
        check(s.restart().sync(false) == ProcessLockLease.RESTORED);

        s = new State(); l = s.restart(); l.sync(true); s.commitFail = true; failed(l, false);
        check(!s.locked && s.pending);
        int writes = s.writes; s.commitFail = false;
        check(s.restart().sync(false) == ProcessLockLease.RESTORED && s.writes == writes);

        s = new State(); l = s.restart(); l.sync(true); s.failRead = s.reads + 1; failed(l, false);
        check(s.pending && s.locked);
        check(s.restart().sync(false) == ProcessLockLease.RESTORED);

        s = new State(); s.pending = true; l = s.restart();
        check(l.sync(true) == ProcessLockLease.ACQUIRED); // crash before lock, same/new active native session
        check(l.sync(false) == ProcessLockLease.RESTORED);
        s = new State(); s.ignoreWrite = true; l = s.restart(); failed(l, true);
        check(s.pending && !s.locked);
        check(l.sync(false) == ProcessLockLease.RESTORED);
        System.out.println("ProcessLockLeaseTest: " + count + " checks passed");
    }
}
