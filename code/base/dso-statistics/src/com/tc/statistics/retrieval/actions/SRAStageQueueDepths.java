/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.statistics.retrieval.actions;

import com.tc.async.api.StageManager;
import com.tc.async.api.StageQueueStats;
import com.tc.async.api.Stage;
import com.tc.statistics.StatisticData;
import com.tc.statistics.StatisticRetrievalAction;
import com.tc.statistics.StatisticType;
import com.tc.statistics.DynamicSRA;
import com.tc.stats.Stats;
import com.tc.util.Assert;

import java.util.Date;
import java.util.Collection;
import java.util.Iterator;

public class SRAStageQueueDepths implements DynamicSRA {

  public static final String ACTION_NAME = "stage queue depth";

  private final StageManager stageManager;
  private volatile boolean collectionEnabled = false;
  private static final StatisticData[] EMPTY_STATISTIC_DATA = new StatisticData[0];

  public SRAStageQueueDepths(final StageManager stageManager) {
    Assert.assertNotNull(stageManager);
    this.stageManager = stageManager;
  }

  public String getName() {
    return ACTION_NAME;
  }

  public StatisticType getType() {
    return StatisticType.SNAPSHOT;
  }

  public StatisticData[] retrieveStatisticData() {
    if (!collectionEnabled) {
      return EMPTY_STATISTIC_DATA;
    }
    Date moment = new Date();
    Stats[] stats = stageManager.getStats();
    StatisticData[] data = new StatisticData[stats.length];
    for (int i = 0; i < stats.length; i++) {
      StageQueueStats stageStat = (StageQueueStats)stats[i];
      data[i] = new StatisticData(ACTION_NAME + " : " + stageStat.getName(), moment, new Long(stageStat.getDepth()));
    }
    return data;
  }

  public void enableStatisticCollection() {
    if (collectionEnabled) return;
    synchronized (stageManager) {
      Collection stages = stageManager.getStages();
      for (Iterator it = stages.iterator(); it.hasNext();) {
        ((Stage)it.next()).getSink().enableStatsCollection(true);
      }
    }
    collectionEnabled = true;
  }

  public void disableStatisticCollection() {
    if (!collectionEnabled) return;
     synchronized (stageManager) {
        Collection stages = stageManager.getStages();
        for (Iterator it = stages.iterator(); it.hasNext();) {
          ((Stage)it.next()).getSink().enableStatsCollection(false);
        }
      }
    collectionEnabled = false;
  }

  public boolean isStatisticCollectionEnabled() {
    return collectionEnabled;
  }
}
