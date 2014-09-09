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

import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.google.common.io.CharStreams;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;

/**
 * Tests for {@link YamlDecorator}.
 */
public class YamlDecoratorTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public Retry retry = new Retry(3);

  private FreeStyleProject project;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    project = jenkins.createFreeStyleProject("test");

    project.getBuildersList().add(new hudson.tasks.Shell("echo $foo"));
    // Make sure we don't clobber pre-existing properties when we workaround the
    // missing null check.
    project.addProperty(new ParametersDefinitionProperty(
        new StringParameterDefinition("foo", "bar")));
  }

  @Test
  public void testProjectCreation() throws Exception {
    HtmlForm form = jenkins.createWebClient().getPage(project, "configure")
        .getFormByName("config");

    // Submit the form and check that the values match our original construction
    jenkins.submit(form);

    // Verify that a YamlAction was attached by our decorator through a method
    // that doesn't attach one if absent.
    YamlAction action = project.getAction(YamlAction.class);
    assertNotNull(action);

    assertSame(YamlAction.of(project), action);

    final InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/param.yaml");
    final String text = CharStreams.toString(
        new InputStreamReader(inputStream));

    assertEquals(text, action.getYaml());
  }
}