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
package com.google.jenkins.plugins.dsl.tag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static com.google.common.io.ByteStreams.copy;

import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.dsl.util.YamlToJson;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

/**
 * Tests for {@link ArgumentYamlTransform}.
 */
public class ArgumentYamlTransformTest {
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  /** */
  public static class Foo {
  }

  /** */
  public static class Bar {
  }

  /** */
  public static class Baz {
  }

  @Test
  public void testTagTranslations() throws Exception {
    ArgumentYamlTransform simple = new ArgumentYamlTransform("!simple",
        ImmutableList.of(""), ImmutableList.<Class>of(Foo.class));

    ArgumentYamlTransform half1 = new ArgumentYamlTransform("!complex",
        ImmutableList.of("a"), ImmutableList.<Class>of(Bar.class));

    ArgumentYamlTransform half2 = new ArgumentYamlTransform("!complex",
        ImmutableList.of("b"), ImmutableList.<Class>of(Baz.class));

    ArgumentYamlTransform complex = half1.merge(half2);

    YamlToJson underTest = new YamlToJson.Default(
        ImmutableList.<YamlTransform>of(simple, complex));
    for (String test : TESTS) {
      testOneTranslation(underTest, test);
    }
  }

  private void testOneTranslation(YamlToJson underTest, String name)
      throws IOException {
    final InputStream jsonStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/tag/" + name + ".json");
    final InputStream yamlStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/tag/" + name + ".yaml");

    final String json = read(jsonStream);

    System.out.println("Testing: " + name);

    assertEquals(json, underTest.toJson(yamlStream));
  }

  private String read(InputStream stream) throws IOException {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    copy(stream, writer);
    return writer.toString();
  }

  private static Iterable<String> TESTS = ImmutableList.of(
      "simple",
      "complex");
}