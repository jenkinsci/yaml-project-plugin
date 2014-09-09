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
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.jenkins.plugins.delegate.AbstractRunnableItemGroup;
import com.google.jenkins.plugins.delegate.ReadOnlyWorkspaceTask;
import com.google.jenkins.plugins.dsl.restrict.AbstractRestriction;
import com.google.jenkins.plugins.dsl.restrict.NoRestriction;
import com.google.jenkins.plugins.dsl.restrict.RestrictedProject;
import com.google.jenkins.plugins.dsl.tag.YamlTag;
import com.google.jenkins.plugins.dsl.tag.YamlTags;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.SCMedItem;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.scm.NullSCM;
import hudson.views.DefaultViewsTabBar;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

/**
 * A project kind designed to load the bulk of its execution logic from a
 * versioned DSL file.  It expects to:
 * <ol>
 *   <li>Sync an {@link SCM}
 *   <li>Instantiate and run an {@link TopLevelItem} from a DSL file
 *       loaded from a file fetched from source control.
 *   <li>Publish results (e.g. trigger other jobs)
 * </ol>
 * @param <T> The type of element contained
 * @see YamlMultiBranchProject
 * @see YamlAction
 * @see YamlHistoryAction
 * @see YamlBuild
 * @see YamlDecorator
 */
@YamlTags({@YamlTag(tag = "!yaml"), @YamlTag(tag = "!dsl", arg = "yaml")})
public class YamlProject<T extends AbstractProject & TopLevelItem>
    extends AbstractRunnableItemGroup<T, YamlProject<T>, YamlBuild<T>>
    implements TopLevelItem, Queue.FlyweightTask, ReadOnlyWorkspaceTask,
    SCMedItem, RestrictedProject<YamlProject<T>> {
  private static Logger logger = Logger.getLogger(
      YamlProject.class.getName());

  /**
   * Build up our Yaml project shell, which gets populated in
   * {@link #submit(StaplerRequest, StaplerResponse)}.
   */
  public YamlProject(ItemGroup parent, String name,
      @Nullable YamlModule module) throws IOException {
    super(parent, name);
    this.module = (module != null) ? module : new YamlModule();

    this.yamlPath = DEFAULT_YAML;
  }

  public static final String DEFAULT_YAML = ".jenkins.yaml";

  /** Fetch our module for resolving dependency objects. */
  public YamlModule getModule() {
    return module;
  }
  private final YamlModule module;

  /** {@inheritDoc} */
  @Override
  protected Class<YamlBuild<T>> getBuildClass() {
    return (Class<YamlBuild<T>>) new TypeToken<YamlBuild<T>>() {}.getRawType();
  }

  /** {@inheritDoc} */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) checkNotNull(Jenkins.getInstance())
        .getDescriptorOrDie(getClass());
  }

  /** {@inheritDoc} */
  @Override
  public void onViewRenamed(View view, String oldName, String newName) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public void deleteView(View view) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public void onDeleted(T item) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public boolean canDelete(View view) {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void onRenamed(T item, String oldName, String newName) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public List<Action> getActions() {
    YamlHistoryAction action = YamlHistoryAction.of(getLastBuild());
    if (action == null) {
      return super.getActions();
    }
    // Delegate to the nested build.
    return action.getProject(this).getActions();
  }

  /** {@inheritDoc} */
  @Override
  public YamlProject<T> asProject() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public AbstractRestriction getRestriction() {
    return restriction;
  }

  /** Sets the path from which our Yaml DSL is loaded */
  public YamlProject<T> setRestriction(AbstractRestriction restriction)
      throws IOException {
    this.restriction = checkNotNull(restriction);
    save();
    return this;
  }
  private AbstractRestriction restriction;

  /** {@inheritDoc} */
  @Override
  protected void init() throws IOException {
    if (this.viewsTabBar == null) {
      this.viewsTabBar = new DefaultViewsTabBar();
    }

    if (this.views == null) {
      this.views = Lists.newArrayList();
    }

    if (this.lastProjectView == null) {
      this.lastProjectView = new LastProjectView(this);
      views.add(lastProjectView);
      lastProjectView.save();
    }

    if (this.jobHistoryView == null) {
      jobHistoryView = new JobHistoryView(this);
      views.add(jobHistoryView);
      jobHistoryView.save();
    }

    if (Strings.isNullOrEmpty(this.primaryViewName)) {
      this.primaryViewName = lastProjectView.getViewName();
    }

    super.init();
  }

  /**
   * Retrieves our specialized {@link JobHistoryView} for displaying the series
   * of jobs we have instantiated as the underlying DSL has changed.
   */
  public JobHistoryView getJobHistoryView() {
    return jobHistoryView;
  }

  /** @see #getJobHistoryView */
  private JobHistoryView jobHistoryView;

  /**
   * Retrieves our specialized {@link LastProjectView} for displaying an
   * embedded view of the last project that was instantiated for the
   * underlying DSL.
   */
  public LastProjectView getLastProjectView() {
    return lastProjectView;
  }

  /** @see #getLastProjectView */
  private LastProjectView lastProjectView;

  /** Retrieves the latest version of our embedded project. */
  public AbstractProject getLastProject() {
    final YamlHistoryAction action =
        YamlHistoryAction.of(getLastBuild());
    // No builds yet.
    if (action == null) {
      return null;
    }
    return action.getProject(this);
  }

  /** {@inheritDoc} */
  @Override
  protected void submit(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException, FormException {
    super.submit(req, rsp);

    final JSONObject json = req.getSubmittedForm();

    setYamlPath(json.optString("yamlPath"));

    if (json.containsKey("restriction")) {
      setRestriction(req.bindJSON(AbstractRestriction.class,
              json.getJSONObject("restriction")));
    } else {
      setRestriction(new NoRestriction());
    }
  }

  /** Fetch the path from which our Yaml DSL can be loaded */
  public String getYamlPath() {
    return yamlPath;
  }

  /** Sets the path from which our Yaml DSL is loaded */
  public YamlProject<T> setYamlPath(String yamlPath) throws IOException {
    this.yamlPath = checkNotNull(yamlPath);
    save();
    return this;
  }

  private String yamlPath;

  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends AbstractProjectDescriptor {
    public DescriptorImpl() {
      load();
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.YamlProject_DisplayName();
    }

    /** {@inheritDoc} */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
        throws FormException {
      json = json.getJSONObject(getDisplayName());
      if (json.optBoolean("verboseLogging", false)) {
        verboseLogging = true;
      } else {
        verboseLogging = false;
      }
      save();
      return true;
    }

    /**
     * @return whether to enable verbose output in the execution log
     * of YamlBuilds
     */
    public boolean isVerbose() {
      return verboseLogging;
    }
    private boolean verboseLogging;


    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Descriptor descriptor) {
      if (!super.isApplicable(descriptor)) {
        return false;
      }
      // Disallow the null SCM from being used with this project.
      if (NullSCM.class.isAssignableFrom(descriptor.clazz)) {
        return false;
      }
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public YamlProject newInstance(ItemGroup parent, String name) {
      try {
        return new YamlProject(parent, name, null /* module */);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
