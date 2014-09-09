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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import hudson.model.FreeStyleProject;

/**
 * Tests for {@link YamlAction}.
 */
public class YamlActionTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  private FreeStyleProject project;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    project = jenkins.createFreeStyleProject("test");
  }

  @Test
  public void testUI() throws Exception {
    final YamlAction action = YamlAction.of(project);

    assertEquals("asYaml", action.getUrlName());
    assertThat(action.getIconFileName(), containsString("yaml"));
    assertThat(action.getDisplayName(), containsString("YAML"));
  }

  @Test
  public void testAttach() throws Exception {
    final YamlAction action = YamlAction.of(project);

    assertSame(action, YamlAction.of(project));
    assertSame(project, action.getParent());
    assertSame(action, project.getAction(YamlAction.class));
  }

  @Test
  public void testContent() throws Exception {
    final YamlAction action = YamlAction.of(project);
    action.setYaml(CONTENT);

    assertEquals(CONTENT, action.getYaml());
  }

  private static final String CONTENT = "foo: bar";
}