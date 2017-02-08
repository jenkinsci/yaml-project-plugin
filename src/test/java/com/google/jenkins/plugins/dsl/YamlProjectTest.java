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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.io.ByteStreams.copy;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.jenkins.plugins.dsl.restrict.NoRestriction;
import com.google.jenkins.plugins.dsl.restrict.PluginBlacklist;
import com.google.jenkins.plugins.storage.GoogleCloudStorageUploader;
import com.google.jenkins.plugins.storage.StdoutUpload;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.scm.NullSCM;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

/**
 * Tests for {@link YamlProject}.
 * @param <T>
 */
public class YamlProjectTest<T extends AbstractProject & TopLevelItem> {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Rule
  public Retry retry = new Retry(3);

  private File yamlFile;
  private File innerFile;

  private YamlProject<T> underTest;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    TypeToken<YamlProject<T>> token =
        new TypeToken<YamlProject<T>>() {};

    underTest = Jenkins.getInstance().createProject(
        (Class<YamlProject<T>>) token.getRawType(), "underTest");

    yamlFile = folder.newFile(".dsl.yaml");
    innerFile = folder.newFile(".inner.yaml");
    underTest.setYamlPath(yamlFile.getAbsolutePath());
    underTest.setRestriction(new NoRestriction());

    // The majority of tests should be what most users will see
    toggleVerbosity(false);
  }

  private void toggleVerbosity(boolean verbose) throws Exception {
    YamlProject.DescriptorImpl descriptor =
        underTest.getDescriptor();

    JSONObject json = new JSONObject();
    json.put("verboseLogging", verbose);

    JSONObject form = new JSONObject();
    form.put(descriptor.getDisplayName(), json);

    assertTrue(descriptor.configure(null, form));
  }

  @Test
  public void testSimple() throws Exception {
    writeResourceToFile("foo.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("Hello World"),
            // Validation that verbose logging is disabled
            not(containsString("!freestyle")),
            not(containsString("!shell")),
            not(containsString("hudson.model.FreeStyleProject")),
            not(containsString("hudson.tasks.Shell"))));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction action = YamlHistoryAction.of(build);
    assertNotNull(action.getProject(underTest));
    assertEquals(1, action.getBuild(underTest).getNumber());
  }

  @Test
  public void testSimpleWithVerboseLogging() throws Exception {
    writeResourceToFile("foo.yaml");
    toggleVerbosity(true);

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("Hello World"),
            // Validation that verbose logging is enabled
            containsString("!freestyle"),
            containsString("!shell"),
            containsString("hudson.model.FreeStyleProject"),
            containsString("hudson.tasks.Shell")));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction action = YamlHistoryAction.of(build);
    assertNotNull(action.getProject(underTest));
    assertEquals(1, action.getBuild(underTest).getNumber());
  }

  @Test
  public void testSimpleWithDefaultParameter() throws Exception {
    writeResourceToFile("param.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0,
        new Cause.LegacyCodeCause()).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())),
        // Check that we wrote the default value.
        containsString("bar"));
  }

  @Test
  public void testSimpleWithParameter() throws Exception {
    writeResourceToFile("param.yaml");

    assertEquals(0, underTest.getItems().size());

    ParametersAction parameters = new ParametersAction(
        new StringParameterValue("foo", "baz"));
    YamlBuild build = underTest.scheduleBuild2(0,
        new Cause.LegacyCodeCause(), parameters).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())),
        // Check that we wrote the passed in parameter instead
        // of the default value.
        containsString("baz"));
  }

  @Test
  public void testSimpleCancellation() throws Exception {
    writeResourceToFile("label.yaml");

    // Make it so that any scheduled inner builds cannot make
    // progress, so we may cancel it to test that the Stage
    // properly terminates
    jenkins.jenkins.setNumExecutors(0);
    jenkins.jenkins.setLabelString("NOT_THE_DROIDS_YOU_ARE_LOOKING_FOR");

    Future<YamlBuild<T>> outerBuild = underTest.scheduleBuild2(0);

    boolean cancelled = false;
    // Find and cancel the scheduled child job.
    for (int i = 0; i < 50 && !cancelled; ++i) {
      boolean empty = true;
      // Walk the queue looking for an item that represents our child execution.
      for (final Queue.Item item : Queue.getInstance().getItems()) {
        empty = false;
        if (item.task == underTest) {
          Uninterruptibles.sleepUninterruptibly(1, SECONDS);
        } else {
          // Cancel the queued item.
          assertTrue(item.isStuck());
          assertTrue(Queue.getInstance().cancel(item));
          // NOTE: We are not seeing the future cancelled as part of the above
          // call, so we fall back on checking that the item with the same 'id'
          // is now a LeftItem (as in: "left" the queue), which shows as
          // isCancelled().
          // assertTrue(item.getFuture().isCancelled());
          Queue.Item newItem = Queue.getInstance().getItem(item.getId());
          assertNotNull(newItem);
          assertThat(newItem, instanceOf(Queue.LeftItem.class));
          assertTrue(((Queue.LeftItem) newItem).isCancelled());
          cancelled = true;
          break;
        }
      }
      // If we see the queue in an empty state, wait for entries to appear.
      if (empty) {
        Uninterruptibles.sleepUninterruptibly(1, SECONDS);
      }
    }
    assertTrue(cancelled);

    YamlBuild build = outerBuild.get(3, SECONDS);
    dumpLog(build);

    assertEquals(Result.ABORTED, build.getResult());
  }

  @Test
  public void testDisallowedNullScm() throws Exception {
    YamlProject.DescriptorImpl descriptor =
        underTest.getDescriptor();

    assertFalse(descriptor.isApplicable(
        Jenkins.getInstance().getDescriptor(NullSCM.class)));
  }

  @Test
  public void testFailure() throws Exception {
    writeResourceToFile("fail.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    // Verify that if the inner build fails, the outer build
    // reports as failure.
    assertEquals(Result.FAILURE, build.getResult());
  }

  @Test
  public void testMissingFile() throws Exception {
    // Intentionally not writing DSL file.
    underTest.setYamlPath(".missing.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    // Verify that if the inner build fails, the outer build
    // reports as failure.
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())),
        containsString("No .missing.yaml file in workspace"));
  }

  @Test
  public void testActionPromotion() throws Exception {
    writeResourceToFile("junit.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    // Verify that if the inner build fails, the outer build
    // reports as failure.
    assertEquals(Result.SUCCESS, build.getResult());
    assertNotNull(build.getAction(hudson.tasks.junit.TestResultAction.class));
  }

  @Test
  public void testNoChange() throws Exception {
    writeResourceToFile("foo.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString("Hello World"));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction firstBuildAction = YamlHistoryAction.of(build);
    assertNotNull(firstBuildAction.getProject(underTest));
    assertEquals(1, firstBuildAction.getBuild(underTest).getNumber());

    // Verify that a second build with no change doesn't instantiate
    // a second sub-project
    build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString("Hello World"));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction secondBuildAction = YamlHistoryAction.of(build);
    assertSame(firstBuildAction.getProject(underTest),
        secondBuildAction.getProject(underTest));
    assertEquals(2, secondBuildAction.getBuild(underTest).getNumber());
  }

  @Test
  public void testSimpleChange() throws Exception {
    writeResourceToFile("foo.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString("Hello World"));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction firstBuildAction = YamlHistoryAction.of(build);
    assertNotNull(firstBuildAction.getProject(underTest));
    assertEquals(1, firstBuildAction.getBuild(underTest).getNumber());

    // Verify that a simple change results in a second project
    // being created.
    writeResourceToFile("bar.yaml");
    build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(
        new InputStreamReader(build.getLogInputStream())),
        allOf(not(containsString("Hello World")),
            containsString("Hola Mundo")));
    assertEquals(2, underTest.getItems().size());
    YamlHistoryAction secondBuildAction = YamlHistoryAction.of(build);
    assertNotSame(firstBuildAction.getProject(underTest),
        secondBuildAction.getProject(underTest));
    assertEquals(1, secondBuildAction.getBuild(underTest).getNumber());

    // Verify that the original project's workspace has been deleted
    assertFalse(firstBuildAction.getBuild(underTest).getWorkspace().exists());
  }

  @Test
  public void testProjectTypeChange() throws Exception {
    writeResourceToFile("foo.yaml");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), containsString("Hello World"));
    assertEquals(1, underTest.getItems().size());
    YamlHistoryAction firstBuildAction = YamlHistoryAction.of(build);
    assertNotNull(firstBuildAction.getProject(underTest));
    assertEquals(1, firstBuildAction.getBuild(underTest).getNumber());

    // Verify that we can change project types as easily as any other
    // change to the DSL.
    writeResourceToFile("matrix.yaml");
    build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertThat(CharStreams.toString(
        new InputStreamReader(build.getLogInputStream())),
        allOf(not(containsString("Hello World")),
            // As a matrix build, this output should be inside of the
            // output of the sub-job.
            not(containsString("Hola Mundo"))));
    assertEquals(2, underTest.getItems().size());
    YamlHistoryAction secondBuildAction = YamlHistoryAction.of(build);
    assertNotSame(firstBuildAction.getProject(underTest),
        secondBuildAction.getProject(underTest));
    assertEquals(1, secondBuildAction.getBuild(underTest).getNumber());
  }

  @Mock
  private PluginWrapper plugin;

  @Mock
  private PluginManager manager;

  /**
   * Special version of PluginBlacklist that hosts our manager mock in a
   * transient field, so that it can be serialized (output only)
   */
  private static class CustomBlacklist extends PluginBlacklist {
    public CustomBlacklist(List<String> plugins, PluginManager manager) {
      super(plugins);
      this.manager = manager;
    }

    @Override
    public PluginManager getPluginManager() {
      return manager;
    }
    // We want to be able to use a mock, but need this to be saveable.
    private transient PluginManager manager;
  }

  @Test
  public void testRestrictionFailure_DescribableList() throws Exception {
    writeResourceToFile("bad.yaml");
    underTest.setRestriction(new CustomBlacklist(
        ImmutableList.of("google-storage-plugin"), manager));
    when(manager.whichPlugin(GoogleCloudStorageUploader.class))
        .thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("google-storage-plugin");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("RestrictedTypeException"),
            containsString(GoogleCloudStorageUploader.class.getName())));
  }

  @Test
  public void testRestrictionFailure_bindJSON() throws Exception {
    writeResourceToFile("bad.yaml");
    underTest.setRestriction(new CustomBlacklist(
        ImmutableList.of("google-storage-plugin"), manager));
    // This time, pretend GCSUploader is built-in,
    // but when we see StdoutUpload, then complain.
    when(manager.whichPlugin(StdoutUpload.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("google-storage-plugin");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("RestrictedTypeException"),
            containsString(StdoutUpload.class.getName())));
  }

  @Test
  public void testRestrictionFailure_topLevel() throws Exception {
    writeResourceToFile("bad.yaml");
    underTest.setRestriction(new CustomBlacklist(
        ImmutableList.of("google-storage-plugin"), manager));
    // This time, pretend FreeStyleProject is from our blacklisted plugin
    when(manager.whichPlugin(FreeStyleProject.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("google-storage-plugin");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("RestrictedTypeException"),
            containsString(FreeStyleProject.class.getName())));
  }

  @Test
  public void testDiagnosticFailure_notLoaded() throws Exception {
    writeResourceToFile("typo.yaml");

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("BadTypeException"),
            containsString("Shellz")));
  }

  @Test
  public void testDiagnosticFailure_outOfPlace() throws Exception {
    writeResourceToFile("out-of-place.yaml");

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("BadTypeException"),
            containsString("Shell")));
  }

  @Test
  public void testDiagnosticFailure_NestedDescribable() throws Exception {
    writeResourceToFile("inner-typo.yaml");

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("BadTypeException"),
            containsString("StdoutUploadz")));
  }

  /**
   * Make sure that a user can't just check in a DSL that delegates to a child
   * DSL job in order to circumvent the parent DSL job's restrictions.
   */
  @Test
  public void testRestrictionFailure_trampolineJob() throws Exception {
    String body = readResource("trampoline.yaml");
    body = body.replaceAll(".inner.yaml",
            StringUtils.escape(innerFile.toString()));
    writeStringToFile(body, yamlFile);
    writeResourceToFile("bad.yaml", innerFile);
    underTest.setRestriction(new CustomBlacklist(
        ImmutableList.of("google-storage-plugin"), manager));
    when(manager.whichPlugin(GoogleCloudStorageUploader.class))
        .thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("google-storage-plugin");

    assertEquals(0, underTest.getItems().size());

    YamlBuild build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())), allOf(
            containsString("RestrictedTypeException"),
            containsString(GoogleCloudStorageUploader.class.getName())));
  }

  @Mock
  private View mockView;

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedOperation_onViewRenamed() throws Exception {
    underTest.onViewRenamed(mockView, "old", "new");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedOperation_deleteView() throws Exception {
    underTest.deleteView(mockView);
  }

  @Test
  public void testUnsupportedOperation_canDelete() throws Exception {
    assertFalse(underTest.canDelete(mockView));
  }

  @Mock
  private T mockItem;

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedOperation_onDeleted() throws Exception {
    underTest.onDeleted(mockItem);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedOperation_onRenamed() throws Exception {
    underTest.onRenamed(mockItem, "old", "new");
  }

  @Test
  public void testViewProperties() throws Exception {
    writeResourceToFile("foo.yaml");
    JobHistoryView view = underTest.getJobHistoryView();

    assertEquals(0, underTest.getItems().size());
    assertEquals(ImmutableList.copyOf(underTest.getItems()), view.getItems());
    // Check that there is no "last project"
    assertNull(underTest.getLastProject());

    YamlBuild build = underTest.scheduleBuild2(0).get();
    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertEquals(ImmutableList.copyOf(underTest.getItems()), view.getItems());
    // Check that the parent of the last build is our "last project"
    YamlHistoryAction action = YamlHistoryAction.of(build);
    assertEquals(action.getProject(underTest), underTest.getLastProject());

    // Verify that a simple change results in a second project
    // being created.
    writeResourceToFile("bar.yaml");
    build = underTest.scheduleBuild2(0).get();

    dumpLog(build);
    assertEquals(Result.SUCCESS, build.getResult());
    assertEquals(ImmutableList.copyOf(underTest.getItems()), view.getItems());
    // Check that the parent of the last build is our "last project"
    action = YamlHistoryAction.of(build);
    assertEquals(action.getProject(underTest), underTest.getLastProject());
  }

  private void writeResourceToFile(String resourceName) throws IOException {
    writeResourceToFile(resourceName, yamlFile);
  }

  private void writeResourceToFile(String resourceName, File file)
      throws IOException {
    final InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/" + resourceName);
    copy(inputStream, Files.newOutputStreamSupplier(file));
  }

  private String readResource(String resourceName)
      throws IOException {
    final InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream(
            "com/google/jenkins/plugins/dsl/" + resourceName);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    copy(inputStream, output);
    return output.toString();
  }

  private void writeStringToFile(String body, File file)
      throws IOException {
    final InputStream inputStream = new ByteArrayInputStream(body.getBytes());
    copy(inputStream, Files.newOutputStreamSupplier(file));
  }

  private void dumpLog(Run run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());

    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }
}
