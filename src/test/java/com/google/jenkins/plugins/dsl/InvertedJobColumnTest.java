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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockitoAnnotations;

import hudson.views.JobColumn;
import hudson.views.ListViewColumn;

/**
 * Tests for {@link InvertedJobColumn}.
 */
public class InvertedJobColumnTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDefaultColumns() throws Exception {
    List<ListViewColumn> defaultColumns =
        ListViewColumn.createDefaultInitialColumnList();
    for (ListViewColumn column : defaultColumns) {
      assertNotEquals(InvertedJobColumn.class, column.getClass());
    }
  }

  @Test
  public void testColumnName() throws Exception {
    JobColumn vanillaColumn = new JobColumn();
    JobColumn.DescriptorImpl vanillaDescriptor =
        (JobColumn.DescriptorImpl) vanillaColumn.getDescriptor();

    InvertedJobColumn newColumn = new InvertedJobColumn();
    InvertedJobColumn.DescriptorImpl newDescriptor =
        (InvertedJobColumn.DescriptorImpl) newColumn.getDescriptor();

    // Check that our column name matches what our base uses for its
    // column name (since all we do is change default sort order).
    assertEquals(vanillaDescriptor.getDisplayName(),
        newDescriptor.getColumnName());

    // Check that our display name indicates that the sorting is reversed
    assertThat(newDescriptor.getDisplayName(),
        containsString("reversed"));
  }
}