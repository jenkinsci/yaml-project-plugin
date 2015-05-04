/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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
package com.google.jenkins.plugins.dsl.util;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Utility class for copying {@link JSONObject}s and {@link JSONArray}s
 * while filtering out superfluous content.
 */
public final class Filter {
  /**
   * Deeply copy the given {@link JSONObject}, filtering out superfluous
   * entries.
   */
  public static JSONObject object(JSONObject object) {
    JSONObject copy = new JSONObject();
    // Walk the set of keys for this object.
    for (Object objKey : object.keySet()) {
      // Verify that we only see string keys.
      checkState(objKey instanceof String, "non-string key");
      String key = (String) objKey;

      if (keyFilter(key, object)) {
        continue;
      }

      // If the key points to an array, then recursively clone it
      // and insert it into our copy with the same key.
      JSONArray array = object.optJSONArray(key);
      if (array != null) {
        JSONArray copyArray = Filter.array(array);
        if (!copyArray.isEmpty()) {
          copy.element(key, copyArray);
        }
        continue;
      }
      // If the key points to an object, then recursively clone it
      // and insert it into our copy with the same key.
      JSONObject subObject = object.optJSONObject(key);
      if (subObject != null) {
        JSONObject copyObject = Filter.object(subObject);
        if (!copyObject.isEmpty()) {
          copy.element(key, copyObject);
        }
        continue;
      }
      String value = object.optString(key);
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }
      // For all other objects, insert them as they are.
      copy.element(key, value);
    }
    return copy;
  }

  /**
   * Deeply copy the given {@link JSONArray}, filtering out superfluous
   * entries.
   */
  public static JSONArray array(JSONArray array) {
    JSONArray copy = new JSONArray();
    for (int i = 0; i < array.size(); ++i) {
      // If the key points to an array, then recursively clone it
      // and insert it into our copy with the same key.
      JSONArray subArray = array.optJSONArray(i);
      if (subArray != null) {
        JSONArray copyArray = Filter.array(subArray);
        if (!copyArray.isEmpty()) {
          copy.add(copyArray);
        }
        continue;
      }
      // If the key points to an object, then recursively clone it
      // and insert it into our copy with the same key.
      JSONObject subObject = array.optJSONObject(i);
      if (subObject != null) {
        JSONObject copyObject = Filter.object(subObject);
        if (!copyObject.isEmpty()) {
          copy.add(copyObject);
        }
        continue;
      }
      String value = array.optString(i);
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }
      // For all other objects, insert them as they are.
      copy.add(value);
    }
    return copy;
  }

  /** Keys to filter out. */
  private static boolean keyFilter(String key, JSONObject object) {
    if ("stapler-class".equals(key)) {
      // Filter any 'stapler-class' that co-exists with a '$class'.
      return object.containsKey("$class");
    }
    return "".equals(key)
        || "scm".equals(key)
        || "stapler-class-bag".equals(key);
  }

  /** Do not instantiate this class. */
  private Filter() {}
}
