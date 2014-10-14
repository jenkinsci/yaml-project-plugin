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
import java.util.Map;

import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.xml.sax.SAXException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

/**
 * This {@link SCM} is intended to be used by items contained within and
 * launched by an {@link AbstractBranchAwareProject}.  This {@link SCM}
 * delegates to the {@link SCM} of the containing
 * {@link AbstractBranchAwareProject}, acting at the revision of its upstream
 * build that caused this build to execute.
 *
 * @param <T> The type of container project from which we are inheriting our SCM
 */
public class DelegateSCM<T extends AbstractBranchAwareProject & ItemGroup>
    extends SCM {
  public DelegateSCM(Class<T> clazz) {
    this.clazz = checkNotNull(clazz);
  }

  /**
   * The concrete type of the project that will be delegating to us.
   *
   * NOTE: Solely used for dynamic validation, only exceptional flow
   * is gated on the value of this field.
   */
  private final Class<T> clazz;

  @DataBoundConstructor
  public DelegateSCM(String clazz) throws ClassNotFoundException {
    this((Class<T>) checkNotNull(Jenkins.getInstance())
        .getPluginManager()
        .uberClassLoader
        .loadClass(clazz));
  }

  /** {@inheritDoc} */
  @Override
  public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
    parentSCMFromBuild(build, false /* attach action */)
        .buildEnvVars(build, env);
  }

  /** {@inheritDoc} */
  @Override
  protected PollingResult compareRemoteRevisionWith(AbstractProject project,
      Launcher launcher, FilePath workspace, TaskListener listener,
      SCMRevisionState baseline) {
    // NOTE: As we delegate the public API surface to another SCM, we don't
    // expect this protected API to be reachable.
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public SCMRevisionState calcRevisionsFromBuild(AbstractBuild build,
      Launcher launcher, TaskListener listener)
      throws IOException, InterruptedException {
    return parentSCMFromBuild(build, false /* attach action */)
        .calcRevisionsFromBuild(build, launcher, listener);
  }

  /** {@inheritDoc} */
  @Override
  public boolean checkout(AbstractBuild build, Launcher launcher,
      FilePath remoteDir, BuildListener listener, File changeLogFile)
      throws IOException, InterruptedException {
    return parentSCMFromBuild(build, true /* attach action */)
        .checkout(build, launcher, remoteDir, listener, changeLogFile);
  }

  /** {@inheritDoc} */
  @Override
  public ChangeLogParser createChangeLogParser() {
    return new ChangeLogParser() {
      @Override
      public ChangeLogSet<? extends ChangeLogSet.Entry> parse(
          AbstractBuild build, File changelogFile)
          throws IOException, SAXException {
        return parentSCMFromBuild(build, false /* attach action */)
            .createChangeLogParser().parse(build, changelogFile);
      }
    };
  }

  /**
   * Walk the ancestors of the current project to identify from which
   * project we inherit our {@link SCM}.
   *
   * @param project The project that is consuming this {@link DelegateSCM}
   * @return The project from which to inherit our actual {@link SCM}
   */
  private T getParentProject(AbstractProject project) {
    // We expect this SCM to be shared by 1 or more layers beneath a project
    // matching our clazz, from which we inherit our source context.
    // NOTE: multiple layers are possible with a matrix job, for example.
    checkArgument(this == project.getScm());

    // Some configuration, e.g. MatrixProject/MatrixConfiguration
    // have several layers of project that we need to walk through
    // get to the real container.  Walk through all of the projects
    // that share this SCM to find the AbstractBranchAwareProject
    // that contains this and assigned this sub-project the DelegateSCM.
    AbstractProject cursor = project;
    do {
      ItemGroup parent = cursor.getParent();
      // We are searching for a project, so at any point in time our
      // container must remain a project.  Validate the cast.
      checkState(parent instanceof AbstractProject);
      cursor = (AbstractProject) parent;
    } while (this == cursor.getScm());

    // Validate that the container we ultimately find matches our
    // expected container type before casting and returning it.
    checkState(clazz.isInstance(cursor));
    return clazz.cast(cursor);
  }

  /**
   * Potentially recursively walk the upstream builds until we find one that
   * originates from {@code parentProject}.
   *
   * @param parentProject The containing project from which we inherit our
   * actual {@link SCM}
   * @param build The build we are tracing back to its possible origin at
   * {@code parentProject}.
   * @return The build of {@code parentProject} that (possibly transititvely)
   * caused {@code build}.
   */
  private AbstractBuild getParentBuild(T parentProject, AbstractBuild build) {
    for (final CauseAction action : build.getActions(CauseAction.class)) {
      for (final Cause cause : action.getCauses()) {
        if (!(cause instanceof Cause.UpstreamCause)) {
          continue;
        }
        final Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;

        // If our parent project caused this build, then return its build that
        // triggered this.
        final String upstreamProjectName = parentProject.getFullName();
        final AbstractProject causeProject =
            ((AbstractProject) checkNotNull(Jenkins.getInstance())
                .getItemByFullName(upstreamCause.getUpstreamProject()));
        if (causeProject == null) {
          throw new IllegalStateException(
              "Unable to lookup upstream cause project");
        }
        AbstractBuild causeBuild = causeProject.getBuildByNumber(
            upstreamCause.getUpstreamBuild());
        if (upstreamCause.getUpstreamProject().equals(upstreamProjectName)) {
          return causeBuild;
        }

        // Otherwise, see if the build that triggered our build was triggered by
        // our parent project (transitively)
        causeBuild = getParentBuild(parentProject, causeBuild);
        if (causeBuild != null) {
          return causeBuild;
        }
      }
    }
    throw new IllegalStateException(Messages.DelegateSCM_NoParentBuild());
  }

  /**
   * Fetch a changeset-bound SCM for actions like checking out code.
   * <p>
   * NOTE: Modeled after Literate Build plugin's "checkout" methods
   *
   * @param build The active build for which we need an actual {@link SCM}
   * @param attachAction Whether to attach the SCMRevisionAction from the
   *                     parent build to this build.
   * @return An {@link SCM} derived from our parent {@code T} project, but
   * additionally bound to the changeset at which our originating build ran.
   */
  SCM parentSCMFromBuild(AbstractBuild build, boolean attachAction) {
    T parentProject = getParentProject(build.getProject());

    // Using actions is unreliable if there are nested projects through
    // which we must see (e.g. Matrix), or if we want am abstraction
    // where children are free to trigger other children, all tracked by a
    // single parent build (no way to easily attach "parent" actions).
    final AbstractBuild parentBuild = getParentBuild(parentProject, build);

    // The parent project must attach this action with its revision state
    // prior to delegation for this to know what changeset at which to build.
    final SCMRevisionAction hashAction =
        parentBuild.getAction(SCMRevisionAction.class);
    checkState(hashAction != null, Messages.DelegateSCM_NoRevision());
    if (attachAction) {
      build.addAction(hashAction);
    }

    // SCMSource is a sort of SCM-factory.  Get it for our
    // AbstractBranchAwareProject and use it to construct an SCM at the
    // appropriate revision.
    final SCMSource source = parentProject.getSource();
    final SCMRevision revisionHash = hashAction.getRevision();
    final SCMHead head = revisionHash.getHead();
    return source.build(head, revisionHash);
  }

  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends SCMDescriptor {
    public DescriptorImpl() {
      super(null /* browser */);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isApplicable(AbstractProject project) {
      return getContainer(project) != null;
    }

    /**
     * Surface the parent class with which this plugin should be instantiated.
     */
    @Nullable public String getClassName(AbstractProject project) {
      checkNotNull(project);
      final AbstractBranchAwareProject parent = getContainer(project);
      return (parent != null) ? parent.getClass().getName() : null;
    }

    /**
     * Find the nearest {@link AbstractBranchAwareProject} containing the
     * project to which the candidate project is descended, or null.
     */
    @Nullable private AbstractBranchAwareProject getContainer(
        AbstractProject project) {
      // Determine whether we are a child of an AbstractBranchAwareProject
      AbstractProject cursor = project;
      do {
        ItemGroup parent = cursor.getParent();
        if (!(parent instanceof AbstractProject)) {
          return null;
        }
        cursor = (AbstractProject) parent;
      } while (!(cursor instanceof AbstractBranchAwareProject));

      return (AbstractBranchAwareProject) cursor;
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.DelegateSCM_DisplayName();
    }
  }
}
