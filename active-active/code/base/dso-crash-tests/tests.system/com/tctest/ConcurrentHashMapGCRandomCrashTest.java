/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tctest;

import com.tc.test.activepassive.ServerCrashMode;
import com.tc.test.activepassive.ServerPersistenceMode;
import com.tc.test.activepassive.ServerDataShareMode;
import com.tc.test.activepassive.MultipleServerTestSetupManager;

public class ConcurrentHashMapGCRandomCrashTest extends GCTestBase {

  public void doSetUp(TransparentTestIface t) throws Exception {
    t.getTransparentAppConfig().setAttribute(ConcurrentHashMapSwapingTestApp.GC_TEST_KEY, "true");
    super.doSetUp(t);
  }

  protected Class getApplicationClass() {
    return ConcurrentHashMapSwapingTestApp.class;
  }

  protected boolean canRunActivePassive() {
    return true;
  }

  public void setupActivePassiveTest(MultipleServerTestSetupManager setupManager) {
    setupManager.setServerCount(3);
    setupManager.setServerCrashMode(ServerCrashMode.RANDOM_SERVER_CRASH);
    setupManager.setServerCrashWaitTimeInSec(30);
    setupManager.setServerShareDataMode(ServerDataShareMode.NETWORK);
    setupManager.setServerPersistenceMode(ServerPersistenceMode.TEMPORARY_SWAP_ONLY);
  }

}
