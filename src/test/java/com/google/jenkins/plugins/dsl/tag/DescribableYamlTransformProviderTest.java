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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/** Tests for {@link DescribableYamlTransformProvider}. */
public class DescribableYamlTransformProviderTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  private static final String GOOD_TAG = "!foo";

  /** */
  @YamlTag(tag = GOOD_TAG)
  public static class Foo extends AbstractDescribableImpl<Foo> {
    /** */
    @Extension
    public static class DescriptorImpl extends Descriptor<Foo> {
      @Override
      public String getDisplayName() {
        return "Hello World";
      }
    }
  }

  private static final String BAD_TAG = "bad";

  /** */
  @YamlTag(tag = BAD_TAG)
  public static class Bar extends AbstractDescribableImpl<Bar> {
    /** */
    @Extension
    public static class DescriptorImpl extends Descriptor<Bar> {
      @Override
      public String getDisplayName() {
        return "Hello World";
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private YamlTransform getByTag(String tag) {
    List<YamlTransform> transforms =
        YamlTransformProvider.get();
    for (YamlTransform xform : transforms) {
      if (tag.equals(xform.getTag())) {
        return xform;
      }
    }
    return null;
  }

  @Test
  public void testGood() {
    final YamlTransform underTest = getByTag(GOOD_TAG);
    assertNotNull(underTest);
    assertEquals(GOOD_TAG, underTest.getTag());
    List<Class> classes = underTest.getClasses();
    assertEquals(1, classes.size());
    assertEquals(Foo.class, classes.get(0));
  }

  @Test
  public void testBad() {
    final YamlTransform underTest = getByTag(BAD_TAG);
    assertNull(underTest);
  }
}