/* 
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at 
 *
 *      http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Terracotta Platform.
 *
 * The Initial Developer of the Covered Software is 
 *      Terracotta, Inc., a Software AG company
 */
package com.tc.stats.counter;

/**
 * Bounded Counter is a counter that has bounds, duh ... The value never goes beyond the bound and any set which falls
 * beyond that rang will be conveniently ignored.
 */
public class BoundedCounter extends CounterImpl {

  private final long minBound, maxBound;

  public BoundedCounter() {
    this(0L);
  }

  public BoundedCounter(long initialValue) {
    this(initialValue, Long.MIN_VALUE, Long.MAX_VALUE);
  }

  public BoundedCounter(long initialValue, long minValue, long maxValue) {
    super(initialValue);
    this.minBound = Math.min(minValue, maxValue);
    this.maxBound = Math.max(minValue, maxValue);
    if (initialValue < minBound) {
      super.setValue(minBound);
    } else if (initialValue > maxBound) {
      super.setValue(maxBound);
    }
  }

  @Override
  public synchronized long decrement() {
    long current = getValue();
    if (current <= minBound) { return current; }
    return super.decrement();
  }

  @Override
  public synchronized long decrement(long amount) {
    long current = getValue();
    if (current - amount <= minBound) {
      super.setValue(minBound);
      return minBound;
    }
    return super.decrement(amount);
  }

  @Override
  public synchronized long getAndSet(long newValue) {
    if (newValue < minBound) {
      newValue = minBound;
    } else if (newValue > maxBound) {
      newValue = maxBound;
    }
    return super.getAndSet(newValue);
  }

  @Override
  public synchronized long increment() {
    long current = getValue();
    if (current >= maxBound) { return current; }
    return super.increment();
  }

  @Override
  public synchronized long increment(long amount) {
    long current = getValue();
    if (current + amount >= maxBound) {
      super.setValue(maxBound);
      return maxBound;
    }
    return super.increment(amount);
  }

  @Override
  public synchronized void setValue(long newValue) {
    if (newValue < minBound) {
      newValue = minBound;
    } else if (newValue > maxBound) {
      newValue = maxBound;
    }
    super.setValue(newValue);
  }

}
