package dev.lance.focusallowlist;

import org.junit.Test;

/** Runs the original standalone checks through Android Studio and Gradle's JUnit runner. */
public final class HostChecksTest {
    @Test public void policy() { PolicyTest.main(new String[0]); }
    @Test public void regression() throws Exception { RegressionTest.main(new String[0]); }
    @Test public void duration() { DurationPolicyTest.main(new String[0]); }
    @Test public void islandRefresh() { IslandRefreshLoopTest.main(new String[0]); }
    @Test public void monitorRecovery() { MonitorRecoveryTest.main(new String[0]); }
    @Test public void processLockLease() throws Exception { ProcessLockLeaseTest.main(new String[0]); }
    @Test public void recents() { RecentsPolicyTest.main(new String[0]); }
}
