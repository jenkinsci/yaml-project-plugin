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
import java.util.List;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static com.google.common.io.ByteStreams.copy;

import com.google.common.collect.ImmutableList;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Mailer;
import hudson.tasks.Maven;
import hudson.tasks.Shell;

/**
 * Tests for {@link YamlToJson}.
 */
public class YamlToJsonTest {
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testNoTagTranslations() throws Exception {
    YamlToJson underTest = new YamlToJson.Default();
    for (String test : NO_TAG_TESTS) {
      testOneTranslation(underTest, test);
    }
  }

  private static class HelperTransform implements YamlTransform {
    public HelperTransform(String tag, Class clazz) {
      this.tag = tag;
      this.clazz = clazz;
    }
    @Override
    public String getTag() {
      return tag;
    }
    protected final String tag;

    @Override
    public List<Class> getClasses() {
      return ImmutableList.of(clazz);
    }
    protected final Class clazz;

    @Override
    public String construct(String value) {
      assertEquals("", value);
      return clazz.getName();
    }

    @Override
    public String represent(Class clazz) {
      assertEquals(this.clazz, clazz);
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testTagTranslations() throws Exception {
    YamlToJson underTest = new YamlToJson.Default(
        ImmutableList.<YamlTransform>of(
            new HelperTransform("!freestyle", FreeStyleProject.class),
            new HelperTransform("!maven", Maven.class),
            new HelperTransform("!git", GitSCM.class) {
              @Override
              public String construct(String value) {
                assertEquals("scm", value);
                return clazz.getName();
              }
            },
            new HelperTransform("!shell", Shell.class),
            new HelperTransform("!trigger", BuildTrigger.class),
            new HelperTransform("!mailer", Mailer.class)));
    for (String test : TAG_TESTS) {
      testOneTranslation(underTest, test);
    }
  }

  private void testOneTranslation(YamlToJson underTest, String name)
      throws IOException {
    final InputStream jsonStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/util/" + name + ".json");
    final InputStream yamlStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/util/" + name + ".yaml");

    final String json = read(jsonStream);

    System.out.println("Testing: " + name);

    assertEquals(json, underTest.toJson(yamlStream));
  }

  private String read(InputStream stream) throws IOException {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    copy(stream, writer);
    return writer.toString();
  }

  private static Iterable<String> NO_TAG_TESTS = ImmutableList.of(
      "empty",
      "simple",
      "nested",
      "with-array",
      "types",
      "complex",
      "naked-types",

      // Beyond this point, the reverse translation is not possible
      "hoist");

  private static Iterable<String> TAG_TESTS = ImmutableList.of(
      "complex-tag");
}