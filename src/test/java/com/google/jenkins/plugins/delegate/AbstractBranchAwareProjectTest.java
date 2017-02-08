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
package com.google.jenkins.plugins.delegate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import com.google.common.io.CharStreams;

import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.scm.NullSCM;
import hudson.tasks.Shell;

import jenkins.model.Jenkins;

/**
 * Tests for {@link AbstractBranchAwareProject}.
 */
public class AbstractBranchAwareProjectTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  /** */
  public static class SemiNullSCM extends NullSCM {
    public SemiNullSCM() {
      ;
    }

    /** {@inheritDoc} */
    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build,
        Map<String, String> env) {
      env.put("HELLO", "WORLD");
    }

    /** */
    @Extension
    public static class DescriptorImpl extends NullSCM.DescriptorImpl {
    }
  }

  // TODO(mattmoor): We need a TestSCM/TestBranch with which we can really
  // exercise this stuff how I'd like.

  @Test
  public void testSimple() throws Exception {
    TestBranchAwareProject topLevelProject =
            Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");

    FreeStyleProject leafProject = Jenkins.getInstance().getDescriptorByType(FreeStyleProject.DescriptorImpl.class).newInstance(
        topLevelProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();

    // Run the outer job, which should delegate to our inner project
    TestBuild outerBuild = topLevelProject.scheduleBuild2(0).get();

    // Test that our outer job was a success
    dumpLog(outerBuild);
    assertEquals(Result.SUCCESS, outerBuild.getResult());

    // Test that our inner job was a success
    AbstractBuild innerBuild = leafProject.getBuildByNumber(1);
    dumpLog(innerBuild);
    assertEquals(Result.SUCCESS, innerBuild.getResult());
  }

  @Test
  public void testNested() throws Exception {
    TestBranchAwareProject topLevelProject =
        Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");

    TestBranchAwareProject middleProject =
        topLevelProject.getDescriptor().newInstance(
            topLevelProject, "malcolm");
    middleProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(middleProject);
    middleProject.onCreatedFromScratch();

    FreeStyleProject leafProject = Jenkins.getInstance()
            .getDescriptorByType(FreeStyleProject.DescriptorImpl.class).newInstance(
                    middleProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    middleProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();

    // Run the outer job, which should delegate to our inner project
    TestBuild outerBuild = topLevelProject.scheduleBuild2(0).get();

    // Test that our outer job was a success
    dumpLog(outerBuild);
    assertEquals(Result.SUCCESS, outerBuild.getResult());

    // Test that our middle job was a success
    AbstractBuild middleBuild = middleProject.getBuildByNumber(1);
    dumpLog(middleBuild);
    assertEquals(Result.SUCCESS, middleBuild.getResult());

    // Test that our inner job was a success
    AbstractBuild innerBuild = leafProject.getBuildByNumber(1);
    dumpLog(innerBuild);
    assertEquals(Result.SUCCESS, innerBuild.getResult());
  }

  @Test
  public void testMatrixNesting() throws Exception {
    TestBranchAwareProject topLevelProject =
        Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");

    MatrixProject leafProject = MatrixProject.DESCRIPTOR.newInstance(
        topLevelProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();

    // Run the outer job, which should delegate to our inner project
    TestBuild outerBuild = topLevelProject.scheduleBuild2(0).get();

    // Test that our outer job was a success
    dumpLog(outerBuild);
    assertEquals(Result.SUCCESS, outerBuild.getResult());

    // Test that our inner job was a success
    AbstractBuild innerBuild = leafProject.getBuildByNumber(1);
    dumpLog(innerBuild);
    assertEquals(Result.SUCCESS, innerBuild.getResult());
  }

  @Test
  public void testDirectTrigger() throws Exception {
    TestBranchAwareProject topLevelProject =
        Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");

    FreeStyleProject leafProject = Jenkins.getInstance()
            .getDescriptorByType(FreeStyleProject.DescriptorImpl.class).newInstance(topLevelProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();

    // Run the inner job directly, which should die trying to find the parent
    // build with a reasonable error message.
    AbstractBuild build = leafProject.scheduleBuild2(0).get();

    // Test that our job was a failure
    dumpLog(build);
    assertEquals(Result.FAILURE, build.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        build.getLogInputStream())),
        containsString("Unable to determine parent build"));
  }

  @Test
  public void testSimpleDelegationWithoutEnvironment() throws Exception {
    TestBranchAwareProject topLevelProject =
        Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");
    // NOTE: Without this, we expect $HELLO to resolve to nothing
    // and the grep for WORLD to fail.
    // topLevelProject.setScm(new SemiNullSCM());

    FreeStyleProject leafProject = Jenkins.getInstance()
            .getDescriptorByType(FreeStyleProject.DescriptorImpl.class).newInstance(topLevelProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();
    leafProject.getBuildersList().add(
        new Shell("echo $HELLO"));

    // Run the outer job, which should delegate to our inner project
    TestBuild outerBuild = topLevelProject.scheduleBuild2(0).get();

    // Test that our outer job was a success
    dumpLog(outerBuild);
    assertEquals(Result.SUCCESS, outerBuild.getResult());

    // Test that our inner job was a success
    AbstractBuild innerBuild = leafProject.getBuildByNumber(1);
    dumpLog(innerBuild);
    assertEquals(Result.SUCCESS, innerBuild.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        innerBuild.getLogInputStream())),
        not(containsString("WORLD")));
  }

  @Test
  public void testSimpleDelegationWithEnvironment() throws Exception {
    TestBranchAwareProject topLevelProject =
        Jenkins.getInstance().createProject(
            TestBranchAwareProject.class, "topLevelProject");
    topLevelProject.setScm(new SemiNullSCM());

    FreeStyleProject leafProject = Jenkins.getInstance()
            .getDescriptorByType(FreeStyleProject.DescriptorImpl.class).newInstance(topLevelProject, "foo");
    leafProject.setScm(new DelegateSCM(TestBranchAwareProject.class));
    topLevelProject.setItem(leafProject);
    leafProject.onCreatedFromScratch();
    leafProject.getBuildersList().add(
        new Shell("echo $HELLO"));

    // Run the outer job, which should delegate to our inner project
    TestBuild outerBuild = topLevelProject.scheduleBuild2(0).get();

    // Test that our outer job was a success
    dumpLog(outerBuild);
    assertEquals(Result.SUCCESS, outerBuild.getResult());

    // Test that our inner job was a success
    AbstractBuild innerBuild = leafProject.getBuildByNumber(1);
    dumpLog(innerBuild);
    assertEquals(Result.SUCCESS, innerBuild.getResult());
    assertThat(CharStreams.toString(new InputStreamReader(
        innerBuild.getLogInputStream())),
        containsString("WORLD"));
  }

  private void dumpLog(Run run) throws IOException {
    BufferedReader reader = new BufferedReader(run.getLogReader());

    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }
}