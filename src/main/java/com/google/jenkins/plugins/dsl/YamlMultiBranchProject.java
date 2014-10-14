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
import java.lang.reflect.Field;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.delegate.DelegateSCM;
import com.google.jenkins.plugins.dsl.restrict.NoRestriction;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.scm.SCMDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import jenkins.branch.BranchProjectFactory;
import jenkins.branch.DefaultDeadBranchStrategy;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

/**
 * A project kind designed scan branches for a marker DSL file, then
 * instantiate a {@link YamlProject} to parse and run it, on each matching
 * branch.
 * @param <T> The type of element contained
 * @see YamlProject
 */
public class YamlMultiBranchProject<T extends AbstractProject & TopLevelItem>
    extends MultiBranchProject<YamlProject<T>, YamlBuild<T>> {
  /**
   * Build up our Yaml multibranch project shell, which gets populated in
   * {@link #submit(StaplerRequest, StaplerResponse)}.
   */
  public YamlMultiBranchProject(ItemGroup parent, String name) {
    super(parent, name);

    // By default, clean up branches that have been deleted.
    DefaultDeadBranchStrategy deadBranchStrategy =
        new DefaultDeadBranchStrategy(
            true /* prune dead branches */,
            "0" /* days to keep */,
            "0" /* num to keep */);

    // Monkey patch the better default.
    // TODO(mattmoor): upstream making this protected...
    {
      Class<?> clazz = YamlMultiBranchProject.class.getSuperclass();
      Field field = null;
      for (Field iter : clazz.getDeclaredFields()) {
        if ("deadBranchStrategy".equals(iter.getName())) {
          field = iter;
          break;
        }
      }
      if (field == null) {
        throw new IllegalStateException(
            "deadBranchStrategy field missing from MultiBranchProject");
      }
      try {
        field.setAccessible(true);
        field.set(this, deadBranchStrategy);
      } catch (IllegalAccessException e) {
        // Impossible, we have given ourselves access.
      }
    }
    deadBranchStrategy.setOwner(this);
  }

  /** {@inheritDoc} */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  /** {@inheritDoc} */
  @Override
  public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
    return new YamlCriteria(getProjectFactory().getYamlPath());
  }

  /** The action of newView.jelly */
  public synchronized void doCreateView(StaplerRequest req,
      StaplerResponse rsp) throws FormException, IOException,
                               ServletException {
    addView(View.create(req, rsp, this));
  }

  /** Makes sure the view name is available */
  public FormValidation doCheckViewName(@QueryParameter String value) {
    if (Strings.isNullOrEmpty(value)) {
      return FormValidation.error(
          Messages.YamlMultiBranchProject_ViewNeedsName());
    }
    if (getView(value) != null) {
      return FormValidation.error(
          Messages.YamlMultiBranchProject_ViewExists(value));
    }
    return FormValidation.ok();
  }

  /** {@inheritDoc} */
  @Override
  protected BranchProjectFactory<YamlProject<T>, YamlBuild<T>>
      newProjectFactory() {
    return new YamlProjectFactory(YamlProject.DEFAULT_YAML,
        new NoRestriction(), ImmutableList.<Publisher>of());
  }

  /** {@inheritDoc} */
  @Override
  public YamlProjectFactory getProjectFactory() {
    return (YamlProjectFactory) super.getProjectFactory();
  }

  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends MultiBranchProjectDescriptor {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.YamlMultiBranchProject_DisplayName();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(Descriptor descriptor) {
      // TODO(mattmoor): Ideally this would also ask the SCM via
      // something like: scmd.isApplicable(YamlMultiBranchProject.class)
      // So for now, just skip DelegateSCM:
      if (descriptor.clazz.equals(DelegateSCM.class)) {
        return false;
      }

      final YamlProject.DescriptorImpl yamlDescriptor =
          (YamlProject.DescriptorImpl) checkNotNull(Jenkins.getInstance())
          .getDescriptor(YamlProject.class);

      return yamlDescriptor.isApplicable(descriptor);
    }

    /** Fetch the publisher options to present in our UI */
    public List<Descriptor<Publisher>> getPublisherOptions() {
      // TODO(mattmoor): Avoid including non-@DataBoundConstructor publishers
      return BuildStepDescriptor.filter(Publisher.all(), YamlProject.class);
    }

    /** {@inheritDoc} */
    @Override
    public List<SCMDescriptor<?>> getSCMDescriptors() {
      // I believe this is unused, and isApplicable is called instead?
      throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public YamlMultiBranchProject newInstance(ItemGroup parent, String name) {
      return new YamlMultiBranchProject(parent, name);
    }
  }
}
