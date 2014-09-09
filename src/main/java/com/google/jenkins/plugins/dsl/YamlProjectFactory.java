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

import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.jenkins.plugins.dsl.restrict.AbstractRestriction;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;

import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;

/**
 * Factory for {@link YamlProject}s used by {@link YamlMultiBranchProject}
 * when a new branch needs a project.
 *
 * @param <T> The type of elements instantiated by our {@link YamlProject}
 */
public class YamlProjectFactory<T extends AbstractProject & TopLevelItem>
    extends BranchProjectFactory<YamlProject<T>, YamlBuild<T>> {
  private static Logger logger = Logger.getLogger(
      YamlProjectFactory.class.getName());

  @DataBoundConstructor
  public YamlProjectFactory(String yamlPath,
      AbstractRestriction restriction,
      @Nullable List<Publisher> publishers) {
    this.yamlPath = checkNotNull(yamlPath);
    this.restriction = checkNotNull(restriction);
    this.publishers = publishers;
  }

  /**
   * @return the workspace-relative path where we should find our DSL file
   * in each branch.
   */
  public String getYamlPath() {
    return yamlPath;
  }
  private final String yamlPath;

  /** @return the restrictions on what types the DSL job can load */
  public AbstractRestriction getRestriction() {
    return restriction;
  }
  private final AbstractRestriction restriction;

  /** The set of publishers with which to instantiate projects */
  public List<Publisher> getPublishers() {
    return publishers;
  }
  @Nullable
  private final List<Publisher> publishers;

  /** {@inheritDoc} */
  @Override
  public YamlProject<T> newInstance(final Branch branch) {
    try {
      final YamlProject<T> project = new YamlProject<T>(
          (YamlMultiBranchProject<T>) getOwner(),
          branch.getName(), null /* module */);

      project.setBranch(branch);

      return decorate(project);
    } catch (IOException e) {
      logger.log(SEVERE, e.getMessage(), e);
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public YamlProject<T> decorate(YamlProject<T> project) {
    project = super.decorate(project);

    try {
      project.setYamlPath(getYamlPath());
      project.setRestriction(getRestriction());

      if (publishers != null) {
        project.getPublishersList().clear();
        project.getPublishersList().addAll(publishers);
      } else if (project.getPublishersList() != null) {
        // If we have no publishers list, then make sure that
        // the inner project doesn't either.
        project.getPublishersList().clear();
      }
    } catch (IOException e) {
      logger.log(SEVERE, e.getMessage(), e);
    }

    return project;
  }

  /** {@inheritDoc} */
  @Override
  public Branch getBranch(YamlProject<T> project) {
    return project.getBranch();
  }

  /** {@inheritDoc} */
  @Override
  public YamlProject<T> setBranch(YamlProject<T> project, Branch branch) {
    try {
      checkArgument(project.getBranch().getHead().equals(branch.getHead()));
      checkArgument(project.getBranch().getSourceId().equals(
          branch.getSourceId()));
      final Branch oldBranch = project.getBranch();
      project.setBranch(branch);
      if (!oldBranch.equals(branch)) {
        project.save();
      }
    } catch (IOException e) {
      logger.log(SEVERE, e.getMessage(), e);
    }
    return project;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isProject(Item item) {
    return item instanceof YamlProject;
  }

  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends BranchProjectFactoryDescriptor {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.ProjectFactory_DisplayName();
    }

    /** Fetch the publisher options to present in our UI */
    public List<Descriptor<Publisher>> getPublisherOptions() {
      return BuildStepDescriptor.filter(Publisher.all(), YamlProject.class);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
      return YamlMultiBranchProject.class.isAssignableFrom(clazz);
    }
  }
}
