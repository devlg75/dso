/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tctest.statistics.buffer.h2;

import com.tc.statistics.StatisticData;
import com.tc.statistics.buffer.StatisticsBuffer;
import com.tc.statistics.buffer.StatisticsBufferListener;
import com.tc.statistics.buffer.StatisticsConsumer;
import com.tc.statistics.buffer.exceptions.TCStatisticsBufferCaptureSessionCreationErrorException;
import com.tc.statistics.buffer.exceptions.TCStatisticsBufferException;
import com.tc.statistics.buffer.h2.H2StatisticsBufferImpl;
import com.tc.statistics.config.impl.StatisticsConfigImpl;
import com.tc.statistics.database.exceptions.TCStatisticsDatabaseNotReadyException;
import com.tc.statistics.database.exceptions.TCStatisticsDatabaseStructureFuturedatedException;
import com.tc.statistics.database.exceptions.TCStatisticsDatabaseStructureOutdatedException;
import com.tc.statistics.database.impl.H2StatisticsDatabase;
import com.tc.statistics.jdbc.JdbcHelper;
import com.tc.statistics.retrieval.StatisticsRetriever;
import com.tc.test.TempDirectoryHelper;
import com.tc.util.TCAssertionError;

import java.io.File;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import junit.framework.TestCase;

public class H2StatisticsBufferTest extends TestCase {
  private StatisticsBuffer buffer;
  private File tmpDir;

  public void setUp() throws Exception {
    tmpDir = new TempDirectoryHelper(getClass(), true).getDirectory();
    buffer = new H2StatisticsBufferImpl(new StatisticsConfigImpl(), tmpDir);
    buffer.open();
  }

  public void tearDown() throws Exception {
    buffer.close();
  }

  public void testInvalidBufferDirectory() throws Exception {
    try {
      new H2StatisticsBufferImpl(new StatisticsConfigImpl(), null);
      fail("expected exception");
    } catch (TCAssertionError e) {
      // dir can't be null
    }

    File tmp_dir = new TempDirectoryHelper(getClass(), true).getDirectory();
    tmp_dir.delete();
    try {
      new H2StatisticsBufferImpl(new StatisticsConfigImpl(), tmp_dir);
      fail("expected exception");
    } catch (TCAssertionError e) {
      // dir doesn't exist
    }

    tmp_dir = new TempDirectoryHelper(getClass(), true).getDirectory();
    tmp_dir.delete();
    tmp_dir.createNewFile();
    try {
      new H2StatisticsBufferImpl(new StatisticsConfigImpl(), tmp_dir);
      fail("expected exception");
    } catch (TCAssertionError e) {
      // path is not a dir
    } finally {
      tmp_dir.delete();
    }

    tmp_dir = new TempDirectoryHelper(getClass(), true).getDirectory();
    tmp_dir.setReadOnly();
    try {
      new H2StatisticsBufferImpl(new StatisticsConfigImpl(), tmp_dir);
      fail("expected exception");
    } catch (TCAssertionError e) {
      // dir is not writable
    } finally {
      tmp_dir.delete();
    }
  }

  public void testOutdatedVersionCheck() throws Exception {
    buffer.close();

    H2StatisticsDatabase database = new H2StatisticsDatabase(tmpDir, H2StatisticsBufferImpl.H2_URL_SUFFIX);
    database.open();
    try {
      JdbcHelper.executeUpdate(database.getConnection(), "UPDATE dbstructureversion SET version = "+ (H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION - 1));
      try {
        buffer.open();
        fail("expected exception");
      } catch (TCStatisticsBufferException e) {
        assertTrue(e.getCause() instanceof TCStatisticsDatabaseStructureOutdatedException);
        TCStatisticsDatabaseStructureOutdatedException cause = (TCStatisticsDatabaseStructureOutdatedException)e.getCause();
        assertEquals(H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION - 1, cause.getActualVersion());
        assertEquals(H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION, cause.getExpectedVersion());
        assertNotNull(cause.getCreationDate());
      }
    } finally {
      database.close();
    }
  }

  public void testFuturedatedVersionCheck() throws Exception {
    buffer.close();

    H2StatisticsDatabase database = new H2StatisticsDatabase(tmpDir, H2StatisticsBufferImpl.H2_URL_SUFFIX);
    database.open();
    try {
      JdbcHelper.executeUpdate(database.getConnection(), "UPDATE dbstructureversion SET version = "+ (H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION + 1));
      try {
        buffer.open();
        fail("expected exception");
      } catch (TCStatisticsBufferException e) {
        assertTrue(e.getCause() instanceof TCStatisticsDatabaseStructureFuturedatedException);
        TCStatisticsDatabaseStructureFuturedatedException cause = (TCStatisticsDatabaseStructureFuturedatedException)e.getCause();
        assertEquals(H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION + 1, cause.getActualVersion());
        assertEquals(H2StatisticsBufferImpl.DATABASE_STRUCTURE_VERSION, cause.getExpectedVersion());
        assertNotNull(cause.getCreationDate());
      }
    } finally {
      database.close();
    }
  }

  public void testOpenClose() throws Exception {
    // several opens and closes are silently detected
    buffer.open();
    buffer.open();
    buffer.close();
    buffer.close();
  }

  public void testCloseUnopenedBuffer() throws Exception {
    buffer.close();

    File tmp_dir = new TempDirectoryHelper(getClass(), true).getDirectory();
    StatisticsBuffer newBuffer = new H2StatisticsBufferImpl(new StatisticsConfigImpl(), tmp_dir);
    newBuffer.close(); // should not throw an exception
  }

  public void testCreateCaptureSessionUnopenedBuffer() throws Exception {
    buffer.close();
    try {
      buffer.createCaptureSession("theid");
      fail("expected exception");
    } catch (TCStatisticsBufferException e) {
      // expected
      assertTrue(e.getCause() instanceof TCStatisticsDatabaseNotReadyException);
    }
  }

  public void testCreateCaptureSession() throws Exception {
    StatisticsRetriever retriever1 = buffer.createCaptureSession("theid1");
    assertNotNull(retriever1);
    assertEquals("theid1", retriever1.getSessionId());

    StatisticsRetriever retriever2 = buffer.createCaptureSession("theid2");
    assertNotNull(retriever2);
    assertEquals("theid2", retriever2.getSessionId());

    StatisticsRetriever retriever3 = buffer.createCaptureSession("theid3");
    assertNotNull(retriever3);
    assertEquals("theid3", retriever3.getSessionId());
  }

  public void testCreateCaptureSessionNotUnique() throws Exception {
    buffer.createCaptureSession("theid1");
    try {
      buffer.createCaptureSession("theid1");
      fail("expected exception");
    } catch (TCStatisticsBufferCaptureSessionCreationErrorException e) {
      // sessionId can't be null
    }
  }

  public void testStoreStatisticsDataNullSessionId() throws Exception {
    try {
      buffer.storeStatistic(new StatisticData()
        .agentIp(InetAddress.getLocalHost().getHostAddress())
        .moment(new Date())
        .name("name"));
      fail("expected exception");
    } catch (NullPointerException e) {
      // sessionId can't be null
    }
  }

  public void testStoreStatisticsDataNullAgentIp() throws Exception {
    buffer.createCaptureSession("someid");
    buffer.storeStatistic(new StatisticData()
      .sessionId("someid")
      .moment(new Date())
      .name("name"));
    buffer.setDefaultAgentIp(null);
    try {
      buffer.storeStatistic(new StatisticData()
        .sessionId("someid")
        .moment(new Date())
        .name("name"));
      fail("expected exception");
    } catch (NullPointerException e) {
      // agentIp can't be null
    }
  }

  public void testStoreStatisticsDataNullMoment() throws Exception {
    buffer.createCaptureSession("someid");
    try {
      buffer.storeStatistic(new StatisticData()
        .sessionId("someid")
        .agentIp(InetAddress.getLocalHost().getHostAddress())
        .name("name")
        .data("test"));
      fail("expected exception");
    } catch (NullPointerException e) {
      // agentIp can't be null
    }
  }

  public void testStoreStatisticsDataNullName() throws Exception {
    buffer.createCaptureSession("someid");
    try {
      buffer.storeStatistic(new StatisticData()
        .sessionId("someid")
        .agentIp(InetAddress.getLocalHost().getHostAddress())
        .moment(new Date())
        .data("test"));
      fail("expected exception");
    } catch (NullPointerException e) {
      // agentIp can't be null
    }
  }

  public void testStoreStatisticsDataNullData() throws Exception {
    buffer.createCaptureSession("someid");
    buffer.storeStatistic(new StatisticData()
      .sessionId("someid")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .moment(new Date())
      .name("name"));
  }

  public void testStoreStatisticsUnopenedBuffer() throws Exception {
    buffer.createCaptureSession("someid");

    buffer.close();
    try {
      buffer.storeStatistic(new StatisticData()
        .sessionId("someid")
        .agentIp(InetAddress.getLocalHost().getHostAddress())
        .moment(new Date())
        .name("name")
        .data("test"));
      fail("expected exception");
    } catch (TCStatisticsBufferException e) {
      // expected
      assertTrue(e.getCause() instanceof TCStatisticsDatabaseNotReadyException);
    }
  }

  public void testStoreStatistics() throws Exception {
    buffer.createCaptureSession("someid1");

    long statid1 = buffer.storeStatistic(new StatisticData()
      .sessionId("someid1")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data("stuff"));
    assertEquals(1, statid1);

    long statid2 = buffer.storeStatistic(new StatisticData()
      .sessionId("someid1")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data("stuff2"));
    assertEquals(2, statid2);

    buffer.createCaptureSession("someid2");

    long statid3 = buffer.storeStatistic(new StatisticData()
      .sessionId("someid2")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat 2")
      .data("stuff3"));
    assertEquals(3, statid3);
  }

  public void testConsumeStatisticsInvalidSessionId() throws Exception {
    try {
      buffer.consumeStatistics(null, null);
      fail("expected exception");
    } catch (NullPointerException e) {
      // session ID can't be null
    }
  }

  public void testConsumeStatisticsNullConsumer() throws Exception {
    try {
      buffer.consumeStatistics("someid", null);
      fail("expected exception");
    } catch (NullPointerException e) {
      // consumer can't be null
    }
  }

  public void testConsumeStatisticsUnopenedBuffer() throws Exception {
    buffer.createCaptureSession("someid1");

    buffer.close();
    try {
      buffer.consumeStatistics("someid1", new TestStaticticConsumer());
      fail("expected exception");
    } catch (TCStatisticsBufferException e) {
      // expected
      assertTrue(e.getCause() instanceof TCStatisticsDatabaseNotReadyException);
    }
  }

  public void testConsumeStatistics() throws Exception {
    buffer.createCaptureSession("sessionid1");
    buffer.createCaptureSession("sessionid2");
    populateBufferWithStatistics("sessionid1", "sessionid2");

    TestStaticticConsumer consumer1 = new TestStaticticConsumer();
    buffer.consumeStatistics("sessionid1", consumer1);
    consumer1.ensureCorrectCounts(100, 50);

    TestStaticticConsumer consumer2 = new TestStaticticConsumer();
    buffer.consumeStatistics("sessionid1", consumer2);
    consumer2.ensureCorrectCounts(0, 0);

    TestStaticticConsumer consumer3 = new TestStaticticConsumer();
    buffer.consumeStatistics("sessionid2", consumer3);
    consumer3.ensureCorrectCounts(70, 0);

    TestStaticticConsumer consumer4 = new TestStaticticConsumer();
    buffer.consumeStatistics("sessionid2", consumer4);
    consumer4.ensureCorrectCounts(0, 0);
  }

  public void testConsumeStatisticsInterruptions() throws Exception {
    buffer.createCaptureSession("sessionid1");
    buffer.createCaptureSession("sessionid2");
    populateBufferWithStatistics("sessionid1", "sessionid2");

    TestStaticticConsumer consumer1 = new TestStaticticConsumer().countLimit1(1);
    buffer.consumeStatistics("sessionid1", consumer1);
    consumer1.ensureCorrectCounts(1, 0);

    TestStaticticConsumer consumer2 = new TestStaticticConsumer().countOffset1(1).countLimit1(98);
    buffer.consumeStatistics("sessionid1", consumer2);
    consumer2.ensureCorrectCounts(98, 0);

    TestStaticticConsumer consumer3 = new TestStaticticConsumer().countOffset1(99).countLimit2(20);
    buffer.consumeStatistics("sessionid1", consumer3);
    consumer3.ensureCorrectCounts(1, 20);

    TestStaticticConsumer consumer4 = new TestStaticticConsumer().countOffset1(100).countOffset2(20);
    buffer.consumeStatistics("sessionid1", consumer4);
    consumer4.ensureCorrectCounts(0, 30);
  }

  public void testConsumeStatisticsExceptions() throws Exception {
    buffer.createCaptureSession("sessionid1");
    buffer.createCaptureSession("sessionid2");
    populateBufferWithStatistics("sessionid1", "sessionid2");

    TestStaticticConsumer consumer1 = new TestStaticticConsumer().countLimit1(1).limitWithExceptions(true);
    try {
      buffer.consumeStatistics("sessionid1", consumer1);
      fail("expected exception");
    } catch (RuntimeException e) {
      assertEquals("stat1 limited", e.getMessage());
    }

    TestStaticticConsumer consumer2 = new TestStaticticConsumer().countOffset1(1)
      .countLimit1(98)
      .limitWithExceptions(true);
    try {
      buffer.consumeStatistics("sessionid1", consumer2);
      fail("expected exception");
    } catch (RuntimeException e) {
      assertEquals("stat1 limited", e.getMessage());
    }
    consumer2.ensureCorrectCounts(98, 0);

    TestStaticticConsumer consumer3 = new TestStaticticConsumer().countOffset1(99)
      .countLimit2(20)
      .limitWithExceptions(true);
    try {
      buffer.consumeStatistics("sessionid1", consumer3);
      fail("expected exception");
    } catch (RuntimeException e) {
      assertEquals("stat2 limited", e.getMessage());
    }
    consumer3.ensureCorrectCounts(1, 20);

    TestStaticticConsumer consumer4 = new TestStaticticConsumer().countOffset1(100)
      .countOffset2(20)
      .limitWithExceptions(true);
    buffer.consumeStatistics("sessionid1", consumer4);
    consumer4.ensureCorrectCounts(0, 30);
  }

  public void testDataTypes() throws Exception {
    buffer.createCaptureSession("sessionid1");
    buffer.createCaptureSession("sessionid2");
    buffer.createCaptureSession("sessionid3");
    buffer.createCaptureSession("sessionid4");

    buffer.storeStatistic(new StatisticData()
      .sessionId("sessionid1")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data("string"));

    final Date date_data = new Date();
    buffer.storeStatistic(new StatisticData()
      .sessionId("sessionid2")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data(date_data));

    buffer.storeStatistic(new StatisticData()
      .sessionId("sessionid3")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data(new Long(28756L)));

    buffer.storeStatistic(new StatisticData()
      .sessionId("sessionid4")
      .agentIp(InetAddress.getLocalHost().getHostAddress())
      .agentDifferentiator("yummy")
      .moment(new Date())
      .name("the stat")
      .data(new BigDecimal("6828.577")));

    buffer.consumeStatistics("sessionid1", new StatisticsConsumer() {
        public boolean consumeStatisticData(StatisticData data) {
          assertTrue(data.getData() instanceof String);
          assertEquals("string", data.getData());
          return true;
        }
      });

    buffer.consumeStatistics("sessionid2", new StatisticsConsumer() {
        public boolean consumeStatisticData(StatisticData data) {
          assertTrue(data.getData() instanceof Date);
          assertEquals(date_data, data.getData());
          return true;
        }
      });

    buffer.consumeStatistics("sessionid3", new StatisticsConsumer() {
        public boolean consumeStatisticData(StatisticData data) {
          assertTrue(data.getData() instanceof Long);
          assertEquals(new Long(28756L), data.getData());
          return true;
        }
      });

    buffer.consumeStatistics("sessionid4", new StatisticsConsumer() {
        public boolean consumeStatisticData(StatisticData data) {
          assertTrue(data.getData() instanceof BigDecimal);
          assertEquals(0, new BigDecimal("6828.577").compareTo((BigDecimal)data.getData()));
          return true;
        }
      });
  }

  public void testStatisticsBufferListeners() throws Exception {
    buffer.createCaptureSession("someid1");
    TestStatisticsBufferListener listener1 = new TestStatisticsBufferListener("someid1");
    buffer.addListener(listener1);
    TestStatisticsBufferListener listener2 = new TestStatisticsBufferListener("someid1");
    buffer.addListener(listener2);

    assertFalse(listener1.isStarted());
    assertFalse(listener1.isStopped());

    buffer.startCapturing("someid1");

    assertTrue(listener1.isStarted());
    assertFalse(listener1.isStopped());

    buffer.stopCapturing("someid1");

    assertTrue(listener1.isStarted());
    assertTrue(listener1.isStopped());
  }

  public void testStartCapturingException() throws Exception {
    buffer.createCaptureSession("sessionid");
    buffer.startCapturing("sessionid");
    try {
      buffer.startCapturing("sessionid");
      fail();
    } catch (TCStatisticsBufferException e) {
      // expected
    }
  }

  public void testStopCapturingPermissive() throws Exception {
    buffer.createCaptureSession("thissessionid1");
    buffer.stopCapturing("thissessionid1");
    buffer.stopCapturing("thissessionid1");

    buffer.createCaptureSession("thissessionid2");
    buffer.startCapturing("thissessionid2");
    buffer.stopCapturing("thissessionid2");
    buffer.stopCapturing("thissessionid2");

    try {
      buffer.stopCapturing("thissessionid3");
      // fail
    } catch (TCStatisticsBufferException e) {
      // expected
    }
  }

  private void populateBufferWithStatistics(String sessionid1, String sessionid2) throws TCStatisticsBufferException, UnknownHostException {
    String ip = InetAddress.getLocalHost().getHostAddress();
    for (int i = 1; i <= 100; i++) {
      buffer.storeStatistic(new StatisticData()
        .sessionId(sessionid1)
        .agentIp(ip)
        .agentDifferentiator("D1")
        .moment(new Date())
        .name("stat1")
        .data(new Long(i)));
    }
    for (int i = 1; i <= 50; i++) {
      buffer.storeStatistic(new StatisticData()
        .sessionId(sessionid1)
        .agentIp(ip)
        .agentDifferentiator("D2")
        .moment(new Date())
        .name("stat2")
        .data(String.valueOf(i)));
    }

    for (int i = 1; i <= 70; i++) {
      buffer.storeStatistic(new StatisticData()
        .sessionId(sessionid2)
        .agentIp(ip)
        .agentDifferentiator("D3")
        .moment(new Date())
        .name("stat1")
        .data(new BigDecimal(String.valueOf(i+".0"))));
    }
  }

  private class TestStaticticConsumer implements StatisticsConsumer {
    private int statCount1 = 0;
    private int statCount2 = 0;

    private int countOffset1 = 0;
    private int countOffset2 = 0;

    private int countLimit1 = 0;
    private int countLimit2 = 0;

    private boolean limitWithExceptions = false;

    public TestStaticticConsumer countOffset1(int countOffset1) {
      this.countOffset1 = countOffset1;
      return this;
    }

    public TestStaticticConsumer countOffset2(int countOffset2) {
      this.countOffset2 = countOffset2;
      return this;
    }

    public TestStaticticConsumer countLimit1(int countLimit1) {
      this.countLimit1 = countLimit1;
      return this;
    }

    public TestStaticticConsumer countLimit2(int countLimit2) {
      this.countLimit2 = countLimit2;
      return this;
    }

    public TestStaticticConsumer limitWithExceptions(boolean limitWithExceptions) {
      this.limitWithExceptions = limitWithExceptions;
      return this;
    }

    public boolean consumeStatisticData(StatisticData data) {
      if (data.getName().equals("stat1")) {
        if (countLimit1 > 0 &&
            countLimit1 == statCount1) {
          if (limitWithExceptions) {
            throw new RuntimeException("stat1 limited");
          } else {
            return false;
          }
        }
        statCount1++;
        if (data.getData() instanceof BigDecimal) {
          assertEquals("D3", data.getAgentDifferentiator());
        } else {
          assertEquals("D1", data.getAgentDifferentiator());
        }
        assertEquals(((Number)data.getData()).longValue(), statCount1 + countOffset1);
      }
      if (data.getName().equals("stat2")) {
        if (countLimit2 > 0 &&
            countLimit2 == statCount2) {
          if (limitWithExceptions) {
            throw new RuntimeException("stat2 limited");
          } else {
            return false;
          }
        }
        statCount2++;
        assertEquals("D2", data.getAgentDifferentiator());
        assertEquals(String.valueOf(data.getData()), String.valueOf(statCount2 + countOffset2));
      }
      return true;
    }

    public void ensureCorrectCounts(int count1, int count2) {
      assertEquals(count1, statCount1);
      assertEquals(count2, statCount2);
    }
  }

  private class TestStatisticsBufferListener implements StatisticsBufferListener {
    private String sessionId;
    private boolean started = false;

    private boolean stopped = false;

    public TestStatisticsBufferListener(String sessionId) {
      this.sessionId = sessionId;
    }

    public boolean isStarted() {
      return started;
    }

    public boolean isStopped() {
      return stopped;
    }

    public void capturingStarted(String sessionId) {
      assertEquals(false, started);
      assertEquals(this.sessionId, sessionId);
      started = true;
    }

    public void capturingStopped(String sessionId) {
      assertEquals(false, stopped);
      assertEquals(this.sessionId, sessionId);
      stopped = true;
    }
  }
}