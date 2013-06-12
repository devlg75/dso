package com.tc.objectserver.event;

import org.junit.Before;
import org.junit.Test;

import com.tc.net.ClientID;
import com.tc.object.net.DSOChannelManager;
import com.tc.server.BasicServerEvent;
import com.tc.server.ServerEvent;
import com.tc.server.ServerEventType;
import com.tc.util.concurrent.Runners;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author Eugene Shelestovich
 */
public class InClusterServerEventNotifierTest {

  private InClusterServerEventNotifier notifier;
  private MockBatcher batcher;

  @Before
  public void setUp() {
    final DSOChannelManager channelManager = mock(DSOChannelManager.class);
    batcher = new MockBatcher(channelManager);
    notifier = new InClusterServerEventNotifier(channelManager, batcher);
  }

  @Test
  public void testShouldCorrectlyRegister() throws Exception {
    notifier.register(new ClientID(1L), "cache1", EnumSet.of(ServerEventType.PUT, ServerEventType.EVICT));
    notifier.register(new ClientID(1L), "cache2", EnumSet.of(ServerEventType.PUT));
    notifier.register(new ClientID(2L), "cache1", EnumSet.of(ServerEventType.EXPIRE));
    notifier.register(new ClientID(2L), "cache1", EnumSet.of(ServerEventType.EVICT));
    notifier.register(new ClientID(2L), "cache3", EnumSet.of(ServerEventType.EXPIRE, ServerEventType.EVICT));
    notifier.register(new ClientID(3L), "cache2", EnumSet.of(ServerEventType.PUT, ServerEventType.EXPIRE));

    notifier.handleServerEvent(new BasicServerEvent(ServerEventType.PUT, 1001, new byte[] { 101 }, "cache1"));
    notifier.handleServerEvent(new BasicServerEvent(ServerEventType.EXPIRE, 1002, "cache3"));
    notifier.handleServerEvent(new BasicServerEvent(ServerEventType.EVICT, 1003, "cache1"));

    // wait for batcher to pick up events
    while (batcher.messages.size() < 2) {
      Thread.sleep(50L);
    }
    assertEquals(2, batcher.messages.size());

    Message msg = batcher.messages.get(0);
    assertEquals(new ClientID(1L), msg.clientId);
    assertEquals(2, msg.events.size());
    ServerEvent event = msg.events.get(0);
    assertEquals(ServerEventType.PUT, event.getType());
    assertEquals(1001, event.getKey());
    assertEquals("cache1", event.getCacheName());
    event = msg.events.get(1);
    assertEquals(ServerEventType.EVICT, event.getType());
    assertEquals(1003, event.getKey());
    assertEquals("cache1", event.getCacheName());

    msg = batcher.messages.get(1);
    assertEquals(new ClientID(2L), msg.clientId);
    assertEquals(2, msg.events.size());
    event = msg.events.get(0);
    assertEquals(ServerEventType.EXPIRE, event.getType());
    assertEquals(1002, event.getKey());
    assertEquals("cache3", event.getCacheName());
    event = msg.events.get(1);
    assertEquals(ServerEventType.EVICT, event.getType());
    assertEquals(1003, event.getKey());
    assertEquals("cache1", event.getCacheName());
  }

  @Test
  public void testShouldCorrectlyUnregister() {

  }

  private static final class MockBatcher extends ServerEventBatcher {
    private final List<Message> messages = new CopyOnWriteArrayList<Message>();

    public MockBatcher(final DSOChannelManager channelManager) {
      super(channelManager, Runners.newSingleThreadScheduledTaskRunner());
    }

    @Override
    void send(final ClientID clientId, final List<ServerEvent> events) {
      messages.add(new Message(clientId, events));
    }
  }

  private static final class Message {
    private final ClientID clientId;
    private final List<ServerEvent> events;

    private Message(final ClientID clientId, List<ServerEvent> events) {
      this.clientId = clientId;
      this.events = events;
    }
  }

}
