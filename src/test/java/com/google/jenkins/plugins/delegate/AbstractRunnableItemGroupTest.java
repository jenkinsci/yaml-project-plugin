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

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Lists;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.model.AllView;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;

/** Tests for {@link AbstractRunnableItemGroup}. */
public class AbstractRunnableItemGroupTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  public MockFolder mockFolder;

  @Mock
  private ViewGroupMixIn mockMixIn;

  private ViewsTabBar tabBar;
  private List<View> views;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    mockFolder = jenkins.createFolder("foo");

    tabBar = new DefaultViewsTabBar();
    views = Lists.newArrayList();
  }

  /** */
  public static class TestImpl extends AbstractRunnableItemGroup
                        <TopLevelItem, TestImpl, TestBuildImpl>
      implements TopLevelItem {
    public TestImpl(ItemGroup parent, String name, ViewsTabBar tabBar,
        List<View> views, ViewGroupMixIn mixIn) throws IOException {
      super(parent, name);

      this.viewsTabBar = tabBar;
      this.views = views;
      views.add(new AllView("foo", this));

      this.primaryViewName = PRIMARY_VIEW_NAME;

      super.init();
      this.viewGroupMixIn = mixIn;
    }

    public AbstractProjectDescriptor getDescriptor() {
      return null;
    }

    @Override
    public Class<TestBuildImpl> getBuildClass() {
      return TestBuildImpl.class;
    }

    @Override
    protected void init() throws IOException {
      // So the parent's call from ctor does nothing.
    }
  }

  /** */
  public static class TestBuildImpl
      extends AbstractBuild<TestImpl, TestBuildImpl> {
    public TestBuildImpl(TestImpl parent) throws IOException {
      super(parent);
    }

    @Override
    public void run() {}
  }

  @Test
  public void testBasics() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);

    assertEquals(0, underTest.getViewActions().size());
    assertSame(underTest, underTest.getItemGroup());
    assertSame(tabBar, underTest.getViewsTabBar());
  }

  @Mock
  private View mockView;

  @Test
  public void testMixInDelegation_onViewRenamed() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    underTest.onViewRenamed(mockView, "old", "new");
    verify(mockMixIn).onViewRenamed(mockView, "old", "new");
  }

  @Test
  public void testMixInDelegation_deleteView() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    underTest.deleteView(mockView);
    verify(mockMixIn).deleteView(mockView);
  }

  @Test
  public void testMixInDelegation_getView() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    when(mockMixIn.getView("name")).thenReturn(mockView);
    assertSame(mockView, underTest.getView("name"));
    verify(mockMixIn).getView("name");
  }

  @Test
  public void testMixInDelegation_getViews() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    when(mockMixIn.getViews()).thenReturn(views);
    assertSame(views, underTest.getViews());
    verify(mockMixIn).getViews();
  }

  @Test
  public void testMixInDelegation_getPrimaryView() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    when(mockMixIn.getPrimaryView()).thenReturn(mockView);
    assertSame(mockView, underTest.getPrimaryView());
    verify(mockMixIn).getPrimaryView();
  }

  @Test
  public void testMixInDelegation_canDelete() throws Exception {
    TestImpl underTest = new TestImpl(mockFolder, "foo",
        tabBar, views, mockMixIn);
    when(mockMixIn.canDelete(mockView)).thenReturn(true);
    assertTrue(underTest.canDelete(mockView));
    verify(mockMixIn).canDelete(mockView);
  }


  // TODO(mattmoor): deletion, child deletion, child rename, addItem, submit


  private static final String  PRIMARY_VIEW_NAME = "my-view";
}