/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tc.objectserver.api;

import com.tc.objectserver.impl.SamplingType;
import com.tc.objectserver.l1.impl.ClientObjectReferenceSet;

import java.util.Map;

public interface EvictableMap {

  int getMaxTotalCount();

  int getSize();

  int getTTLSeconds();

  int getTTISeconds();

  Map<Object, EvictableEntry> getRandomSamples(int count, ClientObjectReferenceSet serverMapEvictionClientObjectRefSet, final SamplingType samplingType);

  boolean startEviction();

  void evictionCompleted();

  String getCacheName();

  boolean isEvictionEnabled();
}
