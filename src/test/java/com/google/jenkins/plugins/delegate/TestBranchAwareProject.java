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

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import jenkins.model.Jenkins;

/** Test */
public class TestBranchAwareProject
    extends AbstractBranchAwareProject<TestBranchAwareProject, TestBuild>
    implements TopLevelItem, ItemGroup<AbstractProject>,
    Queue.FlyweightTask {
  public TestBranchAwareProject(ItemGroup parent, String name)
      throws IOException {
    super(parent, name);
  }

  /** {@inheritDoc} */
  @Override
  protected Class<TestBuild> getBuildClass() {
    return TestBuild.class;
  }

  /** {@inheritDoc} */
  @Override
  protected void buildDependencyGraph(DependencyGraph graph) {
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFingerprintConfigured() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
    return new DescribableList<Publisher, Descriptor<Publisher>>(this);
  }

  /** {@inheritDoc} */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) Jenkins.getInstance()
        .getDescriptorOrDie(getClass());
  }

  /** {@inheritDoc} */
  @Override
  public void onDeleted(AbstractProject item) {
    throw new UnsupportedOperationException("Cannot delete subproject");
  }

  /** {@inheritDoc} */
  @Override
  public void onRenamed(AbstractProject item, String oldName, String newName) {
    throw new UnsupportedOperationException("Cannot rename subproject");
  }

  /** {@inheritDoc} */
  @Override
  public File getRootDirFor(AbstractProject child) {
    return new File(new File(getRootDir(), "test"), child.getName());
  }

  /** {@inheritDoc} */
  @Override
  public AbstractProject getItem(String name) {
    return innerItem;
  }

  /** Set the item */
  public void setItem(AbstractProject item) {
    this.innerItem = checkNotNull(item);
    try {
      item.save();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The inner item this job contains and delegates execution to */
  public AbstractProject innerItem;

  /** {@inheritDoc} */
  @Override
  public String getUrlChildPrefix() {
    return "test";
  }

  /** {@inheritDoc} */
  @Override
  public Collection<AbstractProject> getItems() {
    return innerItem != null ? ImmutableList.of(innerItem)
        : ImmutableList.<AbstractProject>of();
  }

  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends AbstractProjectDescriptor {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return "Test Branch Aware Project";
    }

    /** {@inheritDoc} */
    @Override
    public TestBranchAwareProject newInstance(ItemGroup parent, String name) {
      try {
        return new TestBranchAwareProject(parent, name);
      } catch (IOException e) {
        return null;
      }
    }
  }
}
