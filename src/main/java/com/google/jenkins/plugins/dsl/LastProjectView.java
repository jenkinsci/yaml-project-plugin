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

import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.View;

/**
 * A custom {@link View} that includes the latest version of
 * versioned project under {@link YamlProject}.
 * <p>
 * NOTE: This doesn't have to be a view, but it gives us the
 * flexibility to switch to using a tab bar to toggle between
 * this and {@link JobHistoryView}.
 */
public class LastProjectView extends View {
  public LastProjectView() {
    super(Messages.LastProjectView_Name());
  }

  public LastProjectView(YamlProject parent) {
    super(Messages.LastProjectView_Name(), parent);
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
    return ImmutableList.of();
  }
}