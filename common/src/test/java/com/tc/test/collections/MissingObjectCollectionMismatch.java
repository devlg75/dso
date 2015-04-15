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
package com.tc.test.collections;

import com.tc.util.Stringifier;

/**
 * A {@link CollectionMismatch}that is used when one collection is missing an object that's present in the other
 * collection.
 */
class MissingObjectCollectionMismatch extends CollectionMismatch {
  public MissingObjectCollectionMismatch(Object originating, boolean originatingIsInCollectionOne,
                                         int originatingIndex, Stringifier describer) {
    super(originating, null, originatingIsInCollectionOne, originatingIndex, -1, describer);
  }

  @Override
  public String toString() {
    return "Missing object: there is no counterpart in " + comparedAgainstCollection() + " for " + originatingString();
  }
}