/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.terracotta.session;

import com.tc.logging.TCLogger;
import com.tc.session.SessionSupport;
import com.tc.util.Assert;
import com.terracotta.session.util.ContextMgr;
import com.terracotta.session.util.LifecycleEventMgr;
import com.terracotta.session.util.StringArrayEnumeration;
import com.terracotta.session.util.Timestamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionContext;

public class SessionData implements Session, SessionSupport {
  private final Map                   attributes         = new HashMap();
  private final Map                   internalAttributes = new HashMap();
  private transient Map               transientAttributes;
  private final long                  createTime;
  private final Timestamp             timestamp;

  private long                        lastAccessedTime;
  private long                        maxIdleMillis;
  private transient long              requestStartMillis;

  private transient SessionId         sessionId;
  private transient LifecycleEventMgr eventMgr;
  private transient ContextMgr        contextMgr;
  private transient SessionManager    sessionManager;
  private transient boolean           invalidated        = false;
  private transient boolean           invalidating       = false;

  private static final ThreadLocal    request            = new ThreadLocal();
  private static final long           NEVER_EXPIRE       = -1;

  protected SessionData(int maxIdleSeconds) {
    this.createTime = System.currentTimeMillis();
    this.lastAccessedTime = 0;
    this.timestamp = new Timestamp(System.currentTimeMillis());
    setMaxInactiveSeconds(maxIdleSeconds);
  }

  void associate(SessionId sid, LifecycleEventMgr lifecycleEventMgr, ContextMgr ctxMgr, SessionManager sessionMgr) {
    this.sessionId = sid;
    this.eventMgr = lifecycleEventMgr;
    this.contextMgr = ctxMgr;
    this.sessionManager = sessionMgr;
  }

  public void associateRequest(SessionRequest req) {
    Assert.pre(request.get() == null);
    request.set(req);
  }

  public void clearRequest() {
    request.set(null);
  }

  public SessionId getSessionId() {
    return this.sessionId;
  }

  public SessionData getSessionData() {
    return this;
  }

  public ServletContext getServletContext() {
    return contextMgr.getServletContext();
  }

  public HttpSessionContext getSessionContext() {
    return contextMgr.getSessionContext();
  }

  public boolean isValid() {
    return isValid(false, null);
  }

  public synchronized boolean isValid(boolean debug, TCLogger logger) {
    if (invalidating) {
      if (debug) {
        logger.info(sessionId.getKey() + " is in the process of being invalidated");
      }
      return true;
    }
    if (invalidated) {
      if (debug) {
        logger.info(sessionId.getKey() + " is already invalidated");
      }
      return false;
    }

    if (getMaxInactiveMillis() == NEVER_EXPIRE) {
      if (debug) {
        logger.info(sessionId.getKey() + " is set to never expire");
      }
      return true;
    }

    final long idleMillis = getIdleMillis(debug, logger);
    final long maxInactive = getMaxInactiveMillis();

    final boolean isValid = idleMillis < maxInactive;

    if (debug) {
      logger.info(sessionId.getKey() + " isValid=" + isValid + " (" + idleMillis + " < " + maxInactive + ")");
    }

    return isValid;
  }

  public boolean isNew() {
    checkIfValid();
    return sessionId.isNew();
  }

  synchronized void invalidateIfNecessary() {
    if (invalidated) return;
    invalidate(false);
  }

  public void invalidate() {
    invalidate(true);
  }

  public synchronized void invalidate(boolean unlock) {
    if (invalidated) { throw new IllegalStateException("session already invalidated"); }
    if (invalidating) { return; }

    invalidating = true;

    try {
      eventMgr.fireSessionDestroyedEvent(this);

      String[] attrs = (String[]) attributes.keySet().toArray(new String[attributes.size()]);

      for (int i = 0; i < attrs.length; i++) {
        unbindAttribute(attrs[i]);
      }
    } finally {
      try {
        SessionRequest r = (SessionRequest) request.get();
        if (r != null) {
          r.clearSession();
          clearRequest();
        }
        sessionManager.remove(this, unlock);
      } finally {
        invalidated = true;
        invalidating = false;
      }
    }
  }

  private void checkIfValid() {
    if (!isValid()) throw new IllegalStateException("This session is invalid");
  }

  synchronized void startRequest() {
    requestStartMillis = System.currentTimeMillis();
  }

  public void setMaxInactiveInterval(int v) {
    setMaxInactiveSeconds(v);
    if (isValid() && v == 0) {
      invalidate();
    }
  }

  /**
   * returns idle millis.
   * 
   * @param logger
   * @param debug
   */
  private long getIdleMillis(boolean debug, TCLogger logger) {
    final long lastAccess = lastAccessedTime;

    if (lastAccess == 0) {
      if (debug) {
        logger.info(sessionId.getKey() + " has no last access time");
      }
      return 0;
    }

    final long requestStart = requestStartMillis;

    if (requestStart > lastAccess) {
      final long rv = requestStart - lastAccess;
      if (debug) {
        logger.info(sessionId.getKey() + " has idleMillis=" + rv + " (lastAccess=" + lastAccess + ",requestStart="
                    + requestStart + ")");
      }
      return rv;
    }

    final long diff = System.currentTimeMillis() - lastAccess;
    final long rv = Math.max(diff, 0);

    if (debug) {
      logger.info(sessionId.getKey() + " has idleMillis=" + rv + " (diff=" + diff + ",lastAccess=" + lastAccess + ")");
    }

    return rv;
  }

  synchronized void finishRequest() {
    requestStartMillis = 0;
    final boolean applicationSessionLocked = sessionManager.isApplicationSessionLocked();
    if (!applicationSessionLocked) sessionId.getWriteLock();
    try {
      lastAccessedTime = System.currentTimeMillis();
    } finally {
      if (!applicationSessionLocked) sessionId.commitLock();
    }
  }

  public synchronized long getCreationTime() {
    checkIfValid();
    return createTime;
  }

  public synchronized long getLastAccessedTime() {
    checkIfValid();
    return lastAccessedTime;
  }

  public void setAttribute(String name, Object value) {
    setAttributeReturnOld(name, value);
  }

  public synchronized Object setAttributeReturnOld(String name, Object value) {
    checkIfValid();
    if (value == null) {
      return unbindAttribute(name);
    } else {
      return bindAttribute(name, value);
    }
  }

  public void putValue(String name, Object val) {
    setAttribute(name, val);
  }

  public synchronized Object getAttribute(String name) {
    checkIfValid();
    final boolean applicationSessionLocked = sessionManager.isApplicationSessionLocked();
    if (!applicationSessionLocked) sessionId.getWriteLock();
    try {
      return attributes.get(name);
    } finally {
      if (!applicationSessionLocked) sessionId.commitLock();
    }
  }

  public Object getValue(String name) {
    return getAttribute(name);
  }

  public synchronized String[] getValueNames() {
    checkIfValid();
    final boolean applicationSessionLocked = sessionManager.isApplicationSessionLocked();
    if (!applicationSessionLocked) sessionId.getWriteLock();
    try {
      Set keys = attributes.keySet();
      return (String[]) keys.toArray(new String[keys.size()]);
    } finally {
      if (!applicationSessionLocked) sessionId.commitLock();
    }
  }

  public Enumeration getAttributeNames() {
    return new StringArrayEnumeration(getValueNames());
  }

  public void removeAttribute(String name) {
    removeAttributeReturnOld(name);
  }

  public synchronized Object removeAttributeReturnOld(String name) {
    checkIfValid();
    return unbindAttribute(name);
  }

  public void removeValue(String name) {
    removeAttribute(name);
  }

  synchronized long getMaxInactiveMillis() {
    return maxIdleMillis;
  }

  boolean neverExpires() {
    return getMaxInactiveMillis() == NEVER_EXPIRE;
  }

  public int getMaxInactiveInterval() {
    if (getMaxInactiveMillis() == NEVER_EXPIRE) { return (int) NEVER_EXPIRE; }

    return (int) (getMaxInactiveMillis() / 1000L);
  }

  private synchronized void setMaxInactiveSeconds(int secs) {
    boolean applicationSessionLocked = false;
    if (sessionManager != null) {
      applicationSessionLocked = sessionManager.isApplicationSessionLocked();
      if (!applicationSessionLocked) sessionId.getWriteLock();
    }
    try {
      if (secs < 0) {
        maxIdleMillis = NEVER_EXPIRE;
        this.timestamp.setMillis(Long.MAX_VALUE);
        return;
      }

      maxIdleMillis = secs * 1000L;
      this.timestamp.setMillis(System.currentTimeMillis() + maxIdleMillis);
    } finally {
      if (sessionManager != null) {
        if (!applicationSessionLocked) sessionId.commitLock();
      }
    }
  }

  public synchronized Object getInternalAttribute(String name) {
    checkIfValid();
    return internalAttributes.get(name);
  }

  public synchronized Object setInternalAttribute(String name, Object value) {
    checkIfValid();
    return internalAttributes.put(name, value);
  }

  public synchronized Object removeInternalAttribute(String name) {
    checkIfValid();
    return internalAttributes.remove(name);
  }

  public synchronized Object getTransientAttribute(String name) {
    checkIfValid();
    return getTransientAttributes().get(name);
  }

  public synchronized Object setTransientAttribute(String name, Object value) {
    checkIfValid();
    return getTransientAttributes().put(name, value);
  }

  public synchronized Object removeTransientAttribute(String name) {
    checkIfValid();
    return getTransientAttributes().remove(name);
  }

  public synchronized Collection getTransientAttributeKeys() {
    checkIfValid();
    return new ArrayList(getTransientAttributes().keySet());
  }

  private Object bindAttribute(String name, Object newVal) {
    final boolean sessionLocked = sessionManager.isApplicationSessionLocked();
    if (!sessionLocked) sessionId.getWriteLock();
    try {
      Object oldVal = getAttribute(name);
      if (newVal != oldVal) eventMgr.bindAttribute(this, name, newVal);

      oldVal = attributes.put(name, newVal);

      if (oldVal != newVal) eventMgr.unbindAttribute(this, name, oldVal);

      // now deal with attribute listener events
      if (oldVal != null) eventMgr.replaceAttribute(this, name, oldVal, newVal);
      else eventMgr.setAttribute(this, name, newVal);

      return oldVal;
    } finally {
      if (!sessionLocked) sessionId.commitLock();
    }
  }

  private Object unbindAttribute(String name) {
    final boolean applicationSessionLocked = sessionManager.isApplicationSessionLocked();
    if (!applicationSessionLocked) sessionId.getWriteLock();
    try {
      Object oldVal = attributes.remove(name);
      if (oldVal != null) {
        eventMgr.unbindAttribute(this, name, oldVal);
        eventMgr.removeAttribute(this, name, oldVal);
      }
      return oldVal;
    } finally {
      if (!applicationSessionLocked) sessionId.commitLock();
    }
  }

  public void resumeRequest() {
    TerracottaSessionManager.resumeRequest(this);
  }

  public void pauseRequest() {
    TerracottaSessionManager.pauseRequest(this);
  }

  private Map getTransientAttributes() {
    if (transientAttributes == null) {
      transientAttributes = new HashMap();
    }
    return transientAttributes;
  }

  public String getId() {
    return sessionId.getExternalId();
  }

  Timestamp getTimestamp() {
    return timestamp;
  }

}
