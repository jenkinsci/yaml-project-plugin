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
import java.util.List;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.ByteStreams.copy;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.jenkins.plugins.dsl.util.YamlToJson;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

/** Tests for {@link KeyYamlTransformProvider}. */
public class KeyYamlTransformProviderTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private KeyYamlTransformProvider underTest;

  private List<YamlTransform> transforms;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    underTest = new KeyYamlTransformProvider(
        new Supplier<List<YamlTransform>>() {
          @Override
          public List<YamlTransform> get() {
            return checkNotNull(transforms);
          }
        });
  }

  private static class TestTransform implements YamlTransform {
    @Override
    public String getTag() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Class> getClasses() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String construct(String value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String represent(Class clazz) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testBasicTagWrap() {
    this.transforms = ImmutableList.<YamlTransform>of(
        new TestTransform() {
          @Override
          public String getTag() {
            return "!foo";
          }
        });

    final YamlTransform xform = Iterables.getOnlyElement(underTest.provide());

    assertEquals("!key:foo", xform.getTag());
  }

  @Test
  public void testByNameTranslations() throws Exception {
    YamlTransform a = new TestTransform() {
        @Override
        public String getTag() {
          return "!a";
        }
        @Override
        public String construct(String value) {
          assertEquals("", value);
          return hello.World.class.getName();
        }
        @Override
        public List<Class> getClasses() {
          return ImmutableList.<Class>of(hello.World.class);
        }
      };
    YamlTransform b = new TestTransform() {
        @Override
        public String getTag() {
          return "!b";
        }
        @Override
        public String construct(String value) {
          assertEquals("param", value);
          return goodbye.World.class.getName();
        }
        @Override
        public List<Class> getClasses() {
          return ImmutableList.<Class>of(goodbye.World.class);
        }
      };
    this.transforms = ImmutableList.of(a, b);

    // Only use the by-name transform
    YamlToJson underTest = new YamlToJson.Default(this.underTest._provide());
    for (String test : BY_NAME_TESTS) {
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

  private static Iterable<String> BY_NAME_TESTS = ImmutableList.of("key");
}