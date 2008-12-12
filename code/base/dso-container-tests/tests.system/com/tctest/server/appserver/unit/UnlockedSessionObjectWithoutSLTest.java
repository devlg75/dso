/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tctest.server.appserver.unit;

import com.tc.test.server.util.TcConfigBuilder;

import junit.framework.Test;

public class UnlockedSessionObjectWithoutSLTest extends UnlockedSessionObjectTestBase {

  public static Test suite() {
    return new UnlockedSessionObjectWithoutSLTestSetup();
  }

  public void testSesionLocking() throws Exception {
    super.testSessionLocking();
  }

  @Override
  public boolean isSessionLockingTrue() {
    return false;
  }

  private static class UnlockedSessionObjectWithoutSLTestSetup extends UnlockedSessionObjectTestSetup {

    public UnlockedSessionObjectWithoutSLTestSetup() {
      super(UnlockedSessionObjectWithoutSLTest.class, CONTEXT);
    }

    protected void configureTcConfig(TcConfigBuilder tcConfigBuilder) {
      super.configureTcConfig(tcConfigBuilder);
      tcConfigBuilder.addWebApplicationWithoutSessionLocking(CONTEXT);
    }
  }

}
