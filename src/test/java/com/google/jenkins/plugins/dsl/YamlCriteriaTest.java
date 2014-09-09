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
package com.google.jenkins.plugins.dsl;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import jenkins.scm.api.SCMSourceCriteria.Probe;

/**
 * Tests for {@link YamlCriteria}.
 */
public class YamlCriteriaTest {
  @Mock
  private Probe mockProbe;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(mockProbe.exists(GOOD_YAML_PATH)).thenReturn(true);
    when(mockProbe.exists(BAD_YAML_PATH)).thenReturn(false);
  }

  @Test
  public void testBasicProbe() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    TaskListener listener = new StreamTaskListener(output);
    YamlCriteria criteria = new YamlCriteria(GOOD_YAML_PATH);

    assertTrue(criteria.isHead(mockProbe, listener));
    assertEquals("", output.toString());
  }


  @Test
  public void testBadProbe() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    TaskListener listener = new StreamTaskListener(output);
    YamlCriteria criteria = new YamlCriteria(BAD_YAML_PATH);

    assertFalse(criteria.isHead(mockProbe, listener));
    // Make sure that we mention the file in whatever diagnostic we emit.
    assertThat(output.toString(), containsString(BAD_YAML_PATH));
  }

  private static final String GOOD_YAML_PATH = "path/is/good.yaml";
  private static final String BAD_YAML_PATH = "no/good.yaml";
}