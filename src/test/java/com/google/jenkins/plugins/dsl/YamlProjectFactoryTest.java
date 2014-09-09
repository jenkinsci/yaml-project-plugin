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

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.dsl.restrict.NoRestriction;

import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;

import jenkins.branch.Branch;
import jenkins.branch.BranchProperty;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;

/**
 * Tests for {@link YamlProjectFactory}.
 */
public class YamlProjectFactoryTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  private YamlProjectFactory  underTest;

  @Mock
  private YamlProject mockProject;

  @Mock
  private FreeStyleProject mockBadProject;

  @Mock
  private Branch mockBranch;

  @Mock
  private SCMHead mockHead;

  @Mock
  private MultiBranchProject mockMultiBranchProject;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    underTest = new YamlProjectFactory(YAML_PATH, RESTRICTION,
        ImmutableList.<Publisher>of(new Mailer()));
  }

  @Test
  @WithoutJenkins
  public void testProperties() throws Exception {
    assertEquals(YAML_PATH, underTest.getYamlPath());
    assertSame(RESTRICTION, underTest.getRestriction());
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_getBranch() throws Exception {
    when(mockProject.getBranch()).thenReturn(mockBranch);

    assertSame(mockBranch, underTest.getBranch(mockProject));
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_setBranchSameBranch() throws Exception {
    when(mockProject.getBranch()).thenReturn(mockBranch);
    when(mockBranch.getHead()).thenReturn(mockHead);
    when(mockBranch.getSourceId()).thenReturn(SOURCE_ID);

    assertSame(mockProject, underTest.setBranch(mockProject, mockBranch));

    verify(mockProject, times(3)).getBranch();
    verify(mockProject).setBranch(mockBranch);
    verifyNoMoreInteractions(mockProject);

    verify(mockBranch, times(2)).getHead();
    verify(mockBranch, times(2)).getSourceId();
    verifyNoMoreInteractions(mockBranch);

    verifyNoMoreInteractions(mockHead);
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_setBranchNewBranch() throws Exception {
    // We cannot mock 'equals', so stub it out to false.
    Branch oldBranch = new Branch(SOURCE_ID, mockHead, null /* scm */,
        ImmutableList.<BranchProperty>of()) {
        @Override
        public boolean equals(Object o) {
          return false;
        }
      };

    when(mockProject.getBranch()).thenReturn(oldBranch);
    when(mockBranch.getHead()).thenReturn(mockHead);
    when(mockBranch.getSourceId()).thenReturn(SOURCE_ID);

    assertSame(mockProject, underTest.setBranch(mockProject, mockBranch));

    verify(mockProject, times(3)).getBranch();
    verify(mockProject).setBranch(mockBranch);
    verify(mockProject).save();
    verifyNoMoreInteractions(mockProject);

    verify(mockBranch, times(1)).getHead();
    verify(mockBranch, times(1)).getSourceId();
    verifyNoMoreInteractions(mockBranch);

    verifyNoMoreInteractions(mockHead);
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_setBranchExceptionSaving() throws Exception {
    // We cannot mock 'equals', so stub it out to false.
    Branch oldBranch = new Branch(SOURCE_ID, mockHead, null /* scm */,
        ImmutableList.<BranchProperty>of()) {
        @Override
        public boolean equals(Object o) {
          return false;
        }
      };

    when(mockProject.getBranch()).thenReturn(oldBranch);
    when(mockBranch.getHead()).thenReturn(mockHead);
    when(mockBranch.getSourceId()).thenReturn(SOURCE_ID);

    // TODO(mattmoor): any way to verify that this gets logged?
    doThrow(new IOException("bleh")).when(mockProject).save();

    // Make sure it doesn't die.
    assertSame(mockProject, underTest.setBranch(mockProject, mockBranch));
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_isProjectGood() throws Exception {
    assertTrue(underTest.isProject(mockProject));
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_isProjectBad() throws Exception {
    assertFalse(underTest.isProject(mockBadProject));
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_isApplicableGood() throws Exception {
    YamlProjectFactory.DescriptorImpl descriptor =
        new YamlProjectFactory.DescriptorImpl();
    assertTrue(descriptor.isApplicable(YamlMultiBranchProject.class));
  }

  @Test
  @WithoutJenkins
  public void testProjectAction_isApplicableBad() throws Exception {
    YamlProjectFactory.DescriptorImpl descriptor =
        new YamlProjectFactory.DescriptorImpl();
    assertFalse(descriptor.isApplicable(mockMultiBranchProject.getClass()));
  }

  @Test
  public void testNewInstance() throws Exception {
    YamlMultiBranchProject owner = Jenkins.getInstance().createProject(
        YamlMultiBranchProject.class, "owner");
    underTest.setOwner(owner);

    Branch branch = new Branch(null, new SCMHead("foo"), new NullSCM(),
        ImmutableList.<BranchProperty>of());

    YamlProject project = underTest.newInstance(branch);
    assertNotNull(project);
    assertEquals(YAML_PATH, project.getYamlPath());
    assertSame(RESTRICTION, project.getRestriction());
    assertSame(branch, project.getBranch());
  }

  private static final String YAML_PATH = "path/is/good.yaml";
  private static final NoRestriction RESTRICTION = new NoRestriction();
  private static final String SOURCE_ID = "asdf";
}