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

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.restrict.AbstractRestriction;
import com.google.jenkins.plugins.dsl.restrict.BadTypeException;
import com.google.jenkins.plugins.dsl.restrict.NoRestriction;
import com.google.jenkins.plugins.dsl.restrict.RestrictedProject;

import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.plugins.git.GitSCM;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * Tests for {@link Binder$Default}.
 */
public class BinderTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private Binder.Default underTest;

  private FreeStyleProject project;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    project = jenkins.createFreeStyleProject("test");
    final AbstractRestriction restriction = new NoRestriction();
    underTest = new Binder.Default(restriction.getClassLoader(
        new RestrictedProject<AbstractProject>() {
          @Override
          public AbstractProject asProject() {
            return project;
          }

          @Override
          public AbstractRestriction getRestriction() {
            return restriction;
          }
        }));

    views = Lists.newArrayList();
    viewGroupMixIn = new ViewGroupMixIn(Jenkins.getInstance()) {
        /** {@inheritDoc} */
        @Override
        protected List<View> views() {
          return views;
        }

        /** {@inheritDoc} */
        @Override
        protected String primaryView() {
          throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        protected void primaryView(String name) {
          throw new UnsupportedOperationException();
        }
      };
  }

  @Test
  public void testGetDescriptor_WellFormed() throws Exception {
    Descriptor descriptor = underTest.getDescriptor(Shell.class.getName());
    assertNotNull(descriptor);
  }

  @Test
  public void testGetDescriptor_Typo() throws Exception {
    final String badClass = "hudson.tasks.Shellz";

    try {
      underTest.getDescriptor(badClass);
      fail("expected exception");
    } catch (BadTypeException e) {
      assertThat(e.getMessage(), containsString(badClass));
    }
  }

  @Test
  public void testGetDescriptor_NotDescribable() throws Exception {
    final String goodClass = "java.util.Map";

    try {
      underTest.getDescriptor(goodClass);
      fail("expected exception");
    } catch (BadTypeException e) {
      assertThat(e.getMessage(), containsString(goodClass));
    }
  }

  @Test
  public void testBind_DataBoundConstructor() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(SHELL_JSON);
    Shell shell = underTest.bind(json);
    assertNotNull(shell);
    // Test that the data came through
    assertEquals("echo Hello World", shell.getCommand());
  }

  @Test
  public void testBind_NoDataBoundConstructor() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(NULLSCM_JSON);
    NullSCM scm = underTest.bind(json);
    assertNotNull(scm);
  }

  @Test
  public void testBind_Extension() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(GITSCM_JSON);
    GitSCM scm = underTest.bind(json);
    assertNotNull(scm);
  }

  @Test
  public void testBind_BadCast() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(SHELL_JSON);

    try {
      // Return type is important for cast!
      SCM scm = underTest.bind(json);
      fail("expected exception");
    } catch (ClassCastException e) {
      assertThat(e.getMessage(),
          allOf(containsString(json.optString("kind", null)),
              containsString(SCM.class.getName())));
    }
  }

  @Test
  public void testBindJob_EmptyNoKind() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(FreeStyleProject.class));
    assertEquals(NAME, job.getName());
  }

  @Test
  public void testBindJob_EmptyWithKind() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);

    json.put("kind", FreeStyleProject.class.getName());

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(FreeStyleProject.class));
    assertEquals(NAME, job.getName());
  }

  @Test
  public void testBindJob_EmptyWithMatrixKindNoAxes() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);

    json.put("kind", MatrixProject.class.getName());

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(MatrixProject.class));
    assertEquals(NAME, job.getName());
  }

  @Test
  public void testBindJob_EmptyWithMatrixKindAndAxes() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);
    json.put("kind", MatrixProject.class.getName());

    JSONArray axes = (JSONArray) JSONSerializer.toJSON(AXES_JSON);
    json.put("axis", axes);

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(MatrixProject.class));
    assertEquals(NAME, job.getName());
    assertEquals(2, ((MatrixProject) job).getAxes().size());
  }

  @Test
  public void testBindJob_WithBuilder() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);

    JSONArray builders = (JSONArray) JSONSerializer.toJSON(BUILDERS1_JSON);
    json.put("builder", builders);

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(FreeStyleProject.class));
    assertEquals(NAME, job.getName());

    FreeStyleProject project = (FreeStyleProject) job;

    assertEquals(1, project.getBuilders().size());
    assertThat(project.getBuilders().get(0), instanceOf(Shell.class));
  }

  @Test
  public void testBindJob_FullJob() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_JSON);

    // TODO(mattmoor): The logic in hudson.scm.SCMS uses the index of the SCM
    // among the collection of compatible SCMS for the given project to
    // determine which class to bind instead of relying on the 'kind' field.
    // I have prototyped a fix for this, but until it can be pushed back,
    // we cannot test this.
    // JSONObject scm = (JSONObject) JSONSerializer.toJSON(GITSCM_JSON);
    // json.put("scm", scm);

    JSONArray builders = (JSONArray) JSONSerializer.toJSON(BUILDERS2_JSON);
    json.put("builder", builders);

    JSONArray publishers = (JSONArray) JSONSerializer.toJSON(PUBLISHERS_JSON);
    json.put("publisher", publishers);

    Job job = underTest.bindJob(Jenkins.getInstance(), NAME, json);

    assertNotNull(job);
    assertThat(job, instanceOf(FreeStyleProject.class));
    assertEquals(NAME, job.getName());

    FreeStyleProject project = (FreeStyleProject) job;

    assertEquals(1, project.getBuilders().size());
    assertThat(project.getBuilders().get(0), instanceOf(Maven.class));
    // NOTE: getPublishers returns a Map, but the above returns a list
    assertEquals(1, project.getPublishers().values().size());
    assertThat(project.getPublishers().values().toArray()[0],
        instanceOf(BuildTrigger.class));
  }

  private List<View> views;
  private ViewGroupMixIn viewGroupMixIn;

  @Test
  public void testBindView_simple() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_LIST_VIEW);

    json.put("name", NAME);

    View view = underTest.bindView(viewGroupMixIn, NAME, json);

    assertNotNull(view);
    assertThat(view, instanceOf(ListView.class));
    assertEquals(NAME, view.getViewName());
    assertTrue(views.contains(view));
  }

  @Test
  public void testBindView_simpleWithJob() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_LIST_VIEW);
    json.put("name", NAME);

    // Add our job to the list view
    json.put(project.getName(), true);

    View view = underTest.bindView(viewGroupMixIn, NAME, json);

    assertNotNull(view);
    assertThat(view, instanceOf(ListView.class));
    assertEquals(NAME, view.getViewName());
    assertTrue(views.contains(view));

    ListView listView = (ListView) view;
    assertTrue(listView.jobNamesContains(project));
  }

  @Test
  public void testBindView_simpleWithJobAndColumns() throws Exception {
    JSONObject json = (JSONObject) JSONSerializer.toJSON(EMPTY_LIST_VIEW);
    json.put("name", NAME);

    // Add our job to the list view
    json.put(project.getName(), true);

    // Add some columns to the view.
    JSONArray columns = (JSONArray) JSONSerializer.toJSON(COLUMNS_JSON);
    json.put("columns", columns);

    View view = underTest.bindView(viewGroupMixIn, NAME, json);

    assertNotNull(view);
    assertThat(view, instanceOf(ListView.class));
    assertEquals(NAME, view.getViewName());
    assertTrue(views.contains(view));

    ListView listView = (ListView) view;
    assertTrue(listView.jobNamesContains(project));
    assertEquals(2, listView.getColumns().size());
  }

  // TODO(mattmoor): Test build wrappers

  private static final String NAME = "bazinga";
  private static final String SHELL_JSON =
      "{ 'kind': '" + Shell.class.getName() + "', " +
      " 'command': 'echo Hello World' }";
  private static final String NULLSCM_JSON =
      "{ 'kind': '" + NullSCM.class.getName() + "' }";

  private static final String EMPTY_LIST_VIEW =
      "{ 'kind': '" + ListView.class.getName() + "' }";

  // TODO(mattmoor): Eliminate this once properties is properly null checked
  private static final String EMPTY_JSON =
      "{ 'properties': {} }";

  private static final String AXIS1_JSON =
      "{ 'kind': '" + TextAxis.class.getName() + "', " +
      "'name': 'a', 'valueString': '1 2' }";

  private static final String AXIS2_JSON =
      "{ 'kind': '" + TextAxis.class.getName() + "', " +
      "'name': 'b', 'valueString': '3' }";

  private static final String AXES_JSON =
      "[ " + AXIS1_JSON + ", " + AXIS2_JSON + " ]";

  private static final String BUILDERS1_JSON =
      "[ " + SHELL_JSON + " ]";

  private static final String GITSCM_JSON =
      "{ " +
      " 'kind': '" + GitSCM.class.getName() + "', " +
      " 'userRemoteConfigs': {" +
      "   'url': 'https://github.com/jenkinsci/my-plugin.git' " +
      " }, " +
      " 'branches': {" +
      "   'name': 'master' " +
      " }, " +
      "}";

  private static final String MAVEN_JSON =
      "{ " +
      " 'kind': '" + Maven.class.getName() + "', " +
      " 'targets': 'clean package', " +
      "}";

  private static final String BUILDERS2_JSON =
      "[ " + MAVEN_JSON + " ]";

  private static final String JOB_COLUMN_JSON =
      "{ " +
      " 'kind': '" + JobColumn.class.getName() + "', " +
      "}";

  private static final String STATUS_COLUMN_JSON =
      "{ " +
      " 'kind': '" + StatusColumn.class.getName() + "', " +
      "}";

  private static final String COLUMNS_JSON =
      "[ " + STATUS_COLUMN_JSON + ", " + JOB_COLUMN_JSON + " ]";

  private static final String TRIGGER_JSON =
      "{ " +
      " 'kind': '" + BuildTrigger.class.getName() + "', " +
      " 'childProjects': 'test', " +
      " 'threshold': 'SUCCESS', " +
      "}";

  private static final String PUBLISHERS_JSON =
      "[ " + TRIGGER_JSON + " ]";
}