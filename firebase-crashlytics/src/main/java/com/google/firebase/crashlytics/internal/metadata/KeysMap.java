// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.metadata;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Handles any key/values for metadata. */
class KeysMap {

  // We use synchronized methods in this class rather than a ConcurrentHashMap because the
  // getKeys() method would need to return a defensive copy in either case. So using the standard
  // HashMap with synchronized access is more straightforward, and enables us to continue allowing
  // NULL values.
  private final Map<String, String> keys = new HashMap<>();
  private final Map<String, Long> keysRank = new HashMap<>();
  private long nextKeyRank = 0;

  private final int maxEntries;
  private final int maxEntryLength;

  public KeysMap(int maxEntries, int maxEntryLength) {
    this.maxEntries = maxEntries;
    this.maxEntryLength = maxEntryLength;
  }

  /** @return defensive, unmodifiable copy of the key/value pairs. */
  @NonNull
  public synchronized Map<String, String> getKeys() {
    return Collections.unmodifiableMap(new HashMap<>(keys));
  }

  public synchronized boolean setKey(String key, String value) {
    String sanitizedKey = sanitizeKey(key);

    keysRank.put(sanitizedKey, nextKeyRank);
    nextKeyRank++;

    String santitizedAttribute = sanitizeString(value, maxEntryLength);
    if (CommonUtils.nullSafeEquals(keys.get(sanitizedKey), santitizedAttribute)) {
      return false;
    }

    keys.put(sanitizedKey, value == null ? "" : santitizedAttribute);

    capKeysEntries();
    return true;
  }

  public synchronized void setKeys(Map<String, String> keysAndValues) {
    for (Map.Entry<String, String> entry : keysAndValues.entrySet()) {
      String sanitizedKey = sanitizeKey(entry.getKey());
      String value = entry.getValue();
      keys.put(sanitizedKey, value == null ? "" : sanitizeString(value, maxEntryLength));
      keysRank.put(sanitizedKey, nextKeyRank);
      nextKeyRank++;
    }
    capKeysEntries();
  }

  private synchronized void capKeysEntries() {
    int purgedKeyCount = 0;

    while (keys.size() > maxEntries) {
      Map.Entry<String, Long> oldest = null;
      for (Map.Entry<String, Long> entry : keysRank.entrySet()) {
        if (oldest == null || entry.getValue() < oldest.getValue()) {
          oldest = entry;
        }
      }

      if (oldest != null) {
        keysRank.remove(oldest.getKey());
        keys.remove(oldest.getKey());
        purgedKeyCount++;
      }
    }

    Logger.getLogger()
        .w(
            "Over limit of "
                + maxEntries
                + " custom keys. Purged "
                + purgedKeyCount
                + " oldest key(s).");
  }

  /** Checks that the key is not null then sanitizes it. */
  private String sanitizeKey(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Custom attribute key must not be null.");
    }
    return sanitizeString(key, maxEntryLength);
  }

  /** Trims the string and truncates it to maxLength, or returns null if input is null. */
  public static String sanitizeString(String input, int maxLength) {
    if (input != null) {
      input = input.trim();
      if (input.length() > maxLength) {
        input = input.substring(0, maxLength);
      }
    }
    return input;
  }
}
