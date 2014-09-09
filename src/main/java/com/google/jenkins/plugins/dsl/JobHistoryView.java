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

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.google.common.collect.ImmutableList;

import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.TopLevelItem;
import hudson.util.DescribableList;
import hudson.views.ListViewColumn;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;

/**
 * A simple extension of {@link ListView} intended for displaying the revision
 * history for a {@link YamlProject} DSL job.
 */
public class JobHistoryView extends ListView {
  public JobHistoryView() {
    super(Messages.JobHistoryView_Name());
  }

  public JobHistoryView(YamlProject parent) {
    super(Messages.JobHistoryView_Name(), parent);
  }

  /** {@inheritDoc} */
  @Override
  protected void initColumns() {
    // NOTE: Done this way because "columns" is private (facepalm)
    super.initColumns();
    final DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns =
        getColumns();
    columns.clear();
    columns.add(new StatusColumn());
    columns.add(new WeatherColumn());
    // Replaces JobColumn, so that we can invert the starting sort order.
    columns.add(new InvertedJobColumn());
  }

  /** {@inheritDoc} */
  @Override
  @RequirePOST
  public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  protected void submit(StaplerRequest req) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void onJobRenamed(
      Item item, String oldName, String newName) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public boolean contains(TopLevelItem item) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public List<TopLevelItem> getItems() {
    return ImmutableList.copyOf(getOwnerItemGroup().getItems());
  }
}