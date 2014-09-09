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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.MockitoAnnotations;

import static com.google.common.io.ByteStreams.copy;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.jenkins.plugins.dsl.util.YamlToJson;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/** Tests for {@link DisplayNameYamlTransformProvider}. */
public class DisplayNameYamlTransformProviderTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private YamlTransform underTest;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    underTest = null;
    if (jenkins.jenkins != null) {
      List<YamlTransform> transforms =
          YamlTransformProvider.get();
      for (YamlTransform xform : transforms) {
        if ("!by-name".equals(xform.getTag())) {
          underTest = xform;
        }
      }
      assertNotNull(underTest);
    } else {
      // Avoid using JenkinsRule as much as possible because it
      // is slow and non-deterministically conflicts with
      // ExpectedException.
      underTest = Iterables.getOnlyElement(
          new DisplayNameYamlTransformProvider(
              Suppliers.ofInstance(
                  (List<Descriptor>) ImmutableList.<Descriptor>of(
                      new Foo.DescriptorImpl(),
                      new Bar.DescriptorImpl(),
                      new Baz.DescriptorImpl())))._provide());
    }
  }

  private static final String COMMON_NAME = "Hello World";
  private static final String UNCOMMON_NAME = "Goodbye World";

  /** */
  public static class Foo extends AbstractDescribableImpl<Foo> {
    /** */
    @Extension
    public static class DescriptorImpl extends Descriptor<Foo> {
      @Override
      public String getDisplayName() {
        return COMMON_NAME;
      }
    }
  }

  /** */
  public static class Bar extends AbstractDescribableImpl<Bar> {
    /** */
    @Extension
    public static class DescriptorImpl extends Descriptor<Bar> {
      @Override
      public String getDisplayName() {
        return COMMON_NAME;
      }
    }
  }

  /** */
  public static class Baz extends AbstractDescribableImpl<Baz> {
    /** */
    @Extension
    public static class DescriptorImpl extends Descriptor<Baz> {
      @Override
      public String getDisplayName() {
        return UNCOMMON_NAME;
      }
    }
  }

  @Test
  @WithoutJenkins
  public void testConstructNoCollision() {
    assertEquals(Baz.class.getName(),
        underTest.construct(UNCOMMON_NAME));
  }

  @Test
  @WithoutJenkins
  public void testConstructCollision() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(containsString(COMMON_NAME));
    underTest.construct(COMMON_NAME);
  }

  @Test
  @WithoutJenkins
  public void testRepresentNoCollision() {
    assertEquals(UNCOMMON_NAME, underTest.represent(Baz.class));
  }

  @Test
  @WithoutJenkins
  public void testRepresentCollision1() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(containsString(Bar.class.getName()));
    underTest.represent(Bar.class);
  }

  @Test
  @WithoutJenkins
  public void testRepresentCollision2() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(containsString(Foo.class.getName()));
    underTest.represent(Foo.class);
  }

  @Test
  public void testByNameTranslations() throws Exception {
    // Only use the by-name transform
    YamlToJson underTest = new YamlToJson.Default(
        ImmutableList.of(this.underTest));
    for (String test : BY_NAME_TESTS) {
      testOneTranslation(underTest, test);
    }
  }

  @Test
  public void testConstruct() {
    assertEquals(hudson.model.FreeStyleProject.class.getName(),
        underTest.construct("Build a free-style software project"));
    assertEquals(hudson.tasks.Shell.class.getName(),
        underTest.construct("Execute shell"));
  }

  @Test
  public void testRepresent() {
    assertEquals("Build a free-style software project",
        underTest.represent(hudson.model.FreeStyleProject.class));
    assertEquals("Execute shell",
        underTest.represent(hudson.tasks.Shell.class));
  }

  @Test
  @WithoutJenkins
  public void testRepresentBadClass() {
    final Class badClass = getClass();
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(containsString(badClass.getName()));
    underTest.represent(badClass);
  }

  @Test
  @WithoutJenkins
  public void testConstructBadName() {
    final String badDisplayName = "ThIs Is My BaD dIsPlAy NaMe!!!1!";
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(containsString(badDisplayName));
    underTest.construct(badDisplayName);
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

  private static Iterable<String> BY_NAME_TESTS = ImmutableList.of(
      "complex-by-name");
}