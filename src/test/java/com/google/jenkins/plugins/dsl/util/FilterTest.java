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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static com.google.common.io.ByteStreams.copy;

import com.google.common.collect.ImmutableList;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * Tests for {@link YamlToJson}.
 */
public class FilterTest {
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testIdentityFilters() throws Exception {
    for (String test : TESTS) {
      testOneFilter(test, "");
    }
  }

  @Test
  public void testRealFilters() throws Exception {
    for (String test : TESTS) {
      testOneFilter(test, ".unfiltered");
    }
  }

  private void testOneFilter(String name, String suffix) throws IOException {
    final InputStream jsonStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/util/" + name + ".json");
    final InputStream unfilteredStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/util/" + name + suffix + ".json");

    final String json = read(jsonStream);
    final String unfiltered = read(unfilteredStream);

    System.out.println("Testing: " + name);

    final Object unfilteredObject = JSONSerializer.toJSON(unfiltered);
    final String filtered =
        ((unfilteredObject instanceof JSONObject) ?
            Filter.object((JSONObject) unfilteredObject) :
            Filter.array((JSONArray) unfilteredObject)).toString();

    assertEquals(json, filtered);
  }

  private String read(InputStream stream) throws IOException {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    copy(stream, writer);
    return writer.toString();
  }

  private static Iterable<String> TESTS = ImmutableList.of(
      "empty",
      "simple",
      "nested",
      "with-array");
}