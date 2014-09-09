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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.reflect.TypeToken;
import com.google.jenkins.plugins.delegate.DelegateSCM;

import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.scm.NullSCM;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;

/**
 * Tests for {@link YamlMultiBranchProject}.
 * @param <T>
 */
public class YamlMultiBranchProjectTest<
   T extends AbstractProject & TopLevelItem> {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  public MockFolder mockFolder;

  private YamlMultiBranchProject<T> underTest;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    TypeToken<YamlMultiBranchProject<T>> token =
        new TypeToken<YamlMultiBranchProject<T>>() {};

    underTest = Jenkins.getInstance().createProject(
        (Class<YamlMultiBranchProject<T>>) token.getRawType(), "underTest");

    mockFolder = jenkins.createFolder("foo");
  }

  @Test
  public void testBasics() throws Exception {
    assertThat(underTest.getSCMSourceCriteria(null /* SCMSource */),
        instanceOf(YamlCriteria.class));
    assertThat(underTest.getProjectFactory(),
        instanceOf(YamlProjectFactory.class));
  }

  @Test
  public void testApplicability() throws Exception {
    final YamlMultiBranchProject.DescriptorImpl descriptor =
        underTest.getDescriptor();

    assertFalse(descriptor.isApplicable(
        Jenkins.getInstance().getDescriptor(NullSCM.class)));
    assertFalse(descriptor.isApplicable(
        Jenkins.getInstance().getDescriptor(DelegateSCM.class)));
  }

  @Mock
  private ViewGroupMixIn mockViewGroupMixIn;

  /** */
  public static class TestImpl extends YamlMultiBranchProject {
    public TestImpl(ItemGroup parent, String name,
        ViewGroupMixIn viewGroupMixIn) throws IOException {
      super(parent, name);

      this.myViewGroupMixIn = viewGroupMixIn;
    }

    @Override
    public View getView(String name) {
      return myViewGroupMixIn.getView(name);
    }

    private transient ViewGroupMixIn myViewGroupMixIn;
  }

  @Test
  public void testNewViewValidation_empty() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo", mockViewGroupMixIn);

    assertEquals(FormValidation.Kind.ERROR,
        underTest.doCheckViewName(null).kind);
    assertEquals(FormValidation.Kind.ERROR, underTest.doCheckViewName("").kind);
    verifyNoMoreInteractions(mockViewGroupMixIn);
  }

  @Mock
  private View mockView;

  @Test
  public void testNewViewValidation_exists() throws Exception {
    when(mockViewGroupMixIn.getView(BAD_VIEW_NAME)).thenReturn(mockView);
    TestImpl underTest = new TestImpl(mockFolder, "foo", mockViewGroupMixIn);
    FormValidation result = underTest.doCheckViewName(BAD_VIEW_NAME);
    assertEquals(FormValidation.Kind.ERROR, result.kind);
    assertThat(result.getMessage(), containsString(BAD_VIEW_NAME));
    verify(mockViewGroupMixIn).getView(BAD_VIEW_NAME);
    verifyNoMoreInteractions(mockViewGroupMixIn);
    verifyNoMoreInteractions(mockView);
  }

  @Test
  public void testNewViewValidation_available() throws Exception {
    when(mockViewGroupMixIn.getView("name")).thenReturn(null);
    TestImpl underTest = new TestImpl(mockFolder, "foo", mockViewGroupMixIn);
    FormValidation result = underTest.doCheckViewName("name");
    assertEquals(FormValidation.Kind.OK, result.kind);
    verify(mockViewGroupMixIn).getView("name");
    verifyNoMoreInteractions(mockViewGroupMixIn);
  }

  private static final String BAD_VIEW_NAME = "THIS NAME IS TAKEN";
}