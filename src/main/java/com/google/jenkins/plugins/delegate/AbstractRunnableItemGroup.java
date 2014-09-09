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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static hudson.model.ItemGroupMixIn.KEYED_BY_NAME;
import static hudson.model.ItemGroupMixIn.loadChildren;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.views.ViewsTabBar;

import net.sf.json.JSONObject;

/**
 * This hoists the common logic for our job container project types,
 * some things shared include:
 * <ul>
 *  <li> {@link ItemGroupMixIn} configuration, and reloading
 *  <li> {@link ViewGroupMixIn} configuration, reloading  and stub routines.
 *  <li> Boilerplace {@link Publisher} code.
 * </ul>
 *
 * @param <T> The type of element contained by this entity
 * @param <P> The ultimate project type of this container
 * @param <B> The built type associated with {@code P}
 */
public abstract class AbstractRunnableItemGroup<
     T extends TopLevelItem,
       P extends AbstractRunnableItemGroup<T, P, B>,
         B extends AbstractBuild<P, B>>
    extends AbstractBranchAwareProject<P, B>
    implements ViewGroup, ItemGroup<T> {
  /**
   * Build up our Yaml project shell, which gets populated in
   * {@link #submit(StaplerRequest, StaplerResponse)}.
   */
  public AbstractRunnableItemGroup(ItemGroup parent, String name)
      throws IOException {
    super(parent, name);
    this.projects = Maps.newHashMap();

    init();
  }

  /** {@inheritDoc} */
  @Override
  public List<Action> getViewActions() {
    return ImmutableList.<Action>of();
  }

  /** {@inheritDoc} */
  @Override
  public ItemGroup<? extends TopLevelItem> getItemGroup() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public ViewsTabBar getViewsTabBar() {
    return viewsTabBar;
  }
  protected volatile ViewsTabBar viewsTabBar;

  /** Perform the deletion. */
  @RequirePOST
  @Override
  public synchronized void doDoDelete(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException, InterruptedException {
    delete();
    rsp.sendRedirect2(Joiner.on("/").join(
        req.getContextPath(), getParent().getUrl()));
  }

  /** {@inheritDoc} */
  @Override
  public void onViewRenamed(View view, String oldName, String newName) {
    getViewGroupMixIn().onViewRenamed(view, oldName, newName);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteView(View view) throws IOException {
    getViewGroupMixIn().deleteView(view);
  }

  /** {@inheritDoc} */
  @Override
  public void onDeleted(T item) throws IOException {
    projects.remove(item.getName());
    save();
  }

  /** {@inheritDoc} */
  @Override
  public View getView(String name) {
    return getViewGroupMixIn().getView(name);
  }

  /** {@inheritDoc} */
  @Override
  public Collection<View> getViews() {
    return getViewGroupMixIn().getViews();
  }

  /** {@inheritDoc} */
  @Override
  public View getPrimaryView() {
    return getViewGroupMixIn().getPrimaryView();
  }
  protected List<View> views;
  protected String primaryViewName;

  /** {@inheritDoc} */
  @Override
  public boolean canDelete(View view) {
    return getViewGroupMixIn().canDelete(view);
  }

  /** {@inheritDoc} */
  @Override
  public void onRenamed(T item, String oldName, String newName)
      throws IOException {
    projects.remove(oldName);
    projects.put(newName, item);
    save();
  }

  /** {@inheritDoc} */
  @Override
  public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
    // TODO(mattmoor): switch to utilize something with an atomic
    // compare/exchange semantic.
    if (publishers != null) {
      return publishers;
    }

    // NOTE: I believe this is lazily initialized vs. created in the
    // constructor so that lazy API consumers can omit an empty publisher list
    // from their serialized XML blob.
    synchronized (this) {
      if (publishers == null) {
        publishers =
            new DescribableList<Publisher, Descriptor<Publisher>>(this);
      }
    }

    return publishers;
  }

  @Nullable
  private volatile DescribableList<Publisher, Descriptor<Publisher>> publishers;

  @Nullable
  protected Map<String, T> projects;

  /**
   * Shared method for retrieving the directory in which we store nested
   * sub jobs that we create as part of our DSL processing.
   */
  private File getJobsDir() {
    return new File(getRootDir(), getUrlChildPrefix());
  }

  /**
   * Shared method for retrieving the directory in which a particular
   * sub job's configuration is stored after it was created from its Yaml.
   */
  private File getJobDir(String name) {
    return new File(getJobsDir(), name);
  }

  /** {@inheritDoc} */
  @Override
  public File getRootDirFor(T child) {
    return getJobDir(child.getName());
  }

  /** {@inheritDoc} */
  @Override
  public T getItem(String name) {
    return projects.get(name);
  }

  /** {@inheritDoc} */
  @Override
  public Collection<T> getItems() {
    return Collections.unmodifiableCollection(projects.values());
  }

  /** This surfaces the embedded projects to the Jenkins UI. */
  @Exported
  public T getJob(String name) {
    return getItem(name);
  }

  /** {@inheritDoc} */
  @Override
  public String getUrlChildPrefix() {
    return "job";
  }

  /** {@inheritDoc} */
  @Override
  protected void buildDependencyGraph(DependencyGraph graph) {
    getPublishersList().buildDependencyGraph(this, graph);
    // Anything that implements DependencyDeclarer can contribute to
    // the dependency graph, so if our build or build wrappers (or
    // whatever else) might implement this, then we should delegate to
    // them to populate any dependencies.
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFingerprintConfigured() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void onLoad(ItemGroup<? extends Item> parent, String name)
      throws IOException {
    super.onLoad(parent, name);
    getPublishersList().setOwner(this);

    this.projects = loadChildren(this, getJobsDir(), KEYED_BY_NAME);
    init();
  }

  /**
   * Common initialization method for constructor and {@link #onLoad}.
   *
   * NOTE: This should be overriden and the override should end in a call to
   * {@code super.init()} to setup the mixins.
   */
  protected void init() throws IOException {
    checkNotNull(viewsTabBar);
    checkNotNull(views);
    checkState(views.size() > 0);
    checkState(!Strings.isNullOrEmpty(this.primaryViewName));

    this.itemGroupMixIn = new ItemGroupMixIn(this, this) {
        /** {@inheritDoc} */
        @Override
        protected void add(TopLevelItem item) {
          try {
            checkState(item instanceof AbstractProject);
            AbstractRunnableItemGroup.this.addItem((T) item);
          } catch (IOException e) {
            // TODO(mattmoor): log
            e.printStackTrace();
          }
        }

        /** {@inheritDoc} */
        @Override
        protected File getRootDirFor(String name) {
          // Use the host implementation
          return AbstractRunnableItemGroup.this.getJobDir(name);
        }
      };
    this.viewGroupMixIn = new ViewGroupMixIn(this) {
        /** {@inheritDoc} */
        @Override
        protected List<View> views() {
          return AbstractRunnableItemGroup.this.views;
        }

        /** {@inheritDoc} */
        @Override
        protected String primaryView() {
          return AbstractRunnableItemGroup.this.primaryViewName;
        }

        /** {@inheritDoc} */
        @Override
        protected void primaryView(String name) {
          AbstractRunnableItemGroup.this.primaryViewName = name;
        }
      };
  }

  /** Useful hook for testing, but also for child access */
  protected ItemGroupMixIn getItemGroupMixIn() {
    return itemGroupMixIn;
  }
  protected transient ItemGroupMixIn itemGroupMixIn;

  /** Useful hook for testing, but also for child access */
  protected ViewGroupMixIn getViewGroupMixIn() {
    return viewGroupMixIn;
  }
  protected transient ViewGroupMixIn viewGroupMixIn;

  /** Add an item to our {@link ItemGroup}. */
  public void addItem(T project) throws IOException {
    checkNotNull(project);
    checkNotNull(projects);
    checkArgument(!projects.containsKey(project.getName()));
    projects.put(project.getName(), project);
  }

  /** {@inheritDoc} */
  @Override
  protected void submit(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException, FormException {
    super.submit(req, rsp);

    final JSONObject json = req.getSubmittedForm();

    getPublishersList().rebuildHetero(req, json, Publisher.all(), "publisher");
  }
}
