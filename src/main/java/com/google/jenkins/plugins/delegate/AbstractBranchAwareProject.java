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

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.slaves.WorkspaceList;

import jenkins.branch.Branch;
import jenkins.branch.BranchProperty;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMSource;

/**
 * This new project type is intended to act as a bridge between new
 * {@code MultiBranchProject} types and classic {@link AbstractProject} types.
 * <p>
 * By implementing this, a "branch aware" project is enabled to act on both
 * {@link Branch} and {@link SCM} based workspaces.  Furthermore, when this is
 * a container project (e.g. Literate, Matrix, DSL) the child projects can
 * simply use {@link DelegateSCM} to get the same source snapshot as this
 * container used when it triggered it.
 *
 * @param <P> The project type that derives from this type.
 * @param <B> The build type associated with the project type {@code P}.
 */
public abstract class AbstractBranchAwareProject
    <P extends AbstractBranchAwareProject<P, B>, B extends AbstractBuild<P, B>>
    extends AbstractProject<P, B> {

  public AbstractBranchAwareProject(ItemGroup parent, String name)
      throws IOException {
    super(parent, name);
    setScm(new NullSCM());
  }

  /** {@inheritDoc} */
  @Override
  public boolean checkout(AbstractBuild build, Launcher launcher,
      BuildListener listener, File changelogFile)
      throws IOException, InterruptedException {
    final FilePath workspace = _shareWorkspace(build.getBuiltOn());
    if (workspace != null) {
      listener.getLogger().println(
          Messages.AbstractBranchAwareProject_SharingWorkspace());
      checkState(isDelegate());
      // Instead of performing a full 'checkout', just have the
      // DelegateSCM attach the appropriate SCMRevisionAction.
      ((DelegateSCM) getScm()).parentSCMFromBuild(
          build, true /* attach SCMRevisionAction */);
      return true;
    }

    boolean result = super.checkout(build, launcher, listener, changelogFile);

    // Attach an SCMRevisionAction to this build, so that if/when the nested
    // build checks out with the DelegateSCM it knows what revision to
    // checkout.
    // NOTE: If we are a delegate then DelegateSCM adds the appropriate
    // SCMRevisionAction for us during the above checkout call.
    if (build.getAction(SCMRevisionAction.class) == null) {
      final SCMHead head = getBranch().getHead();
      final SCMSource source = getSource();
      final SCMRevision revision = source.fetch(head, listener);
      if (revision == null) {
        throw new IllegalStateException("Revision action without revision");
      }
      build.addAction(new SCMRevisionAction(revision));
    }
    return result;
  }

  /** Sets the branch of the project, returning it for chained-setter usage. */
  public P setBranch(Branch branch) throws IOException {
    this.branch = checkNotNull(branch);
    checkState(!isDelegate());
    save();  // persist the state change to disk
    return (P) this;
  }

  /** Fetch the branch of the project. */
  public Branch getBranch() {
    if (isDelegate()) {
      final ItemGroup parent = getParent();
      // If we are a delegate, defer to our project context for our Branch
      // since it provides the complete version.
      // NOTE: A delegate SCM is only an option for projects contained
      // within an AbstractBranchAwareProject, so validate this invariant.
      checkState(parent instanceof AbstractBranchAwareProject);
      final AbstractBranchAwareProject parentProject =
          (AbstractBranchAwareProject) parent;
      return parentProject.getBranch();
    } else {
      // If we are not a delegate, than the branch we store is accurate.
      return checkNotNull(branch);
    }
  }

  /** @see #getBranch() */
  private Branch branch;

  /** Retrieve the {@link SCMSource} for the {@link Branch} of our project */
  public SCMSource getSource() {
    // TODO(mattmoor): Consider using getParent(Class<T>) to fetch the
    // appropriately typed parent, and allow it to see through multiple layers
    // of container ItemGroup.
    final ItemGroup parent = getParent();
    if (!isDelegate()) {
      // If we are NOT a delegate project, then if we have an SCMSource id.
      final String sourceId = getBranch().getSourceId();
      if (sourceId != null) {
        // We fetch the identified SCMSource from the
        // containing MultiBranchProject.
        checkState(parent instanceof MultiBranchProject);
        final MultiBranchProject parentProject = (MultiBranchProject) parent;
        final SCMSource source = parentProject.getSCMSource(sourceId);
        checkState(source != null,
            Messages.AbstractBranchAwareProject_NoSCMSource());
        return source;
      } else {
        // Otherwise, we construct a standalone SCMSource from our SCM.
        return new SingleSCMSource(null /* sourceId */, NAME, getScm());
      }
    } else {
      // If we are a delegate project, then we inherit our SCMSource from
      // our context.
      checkState(parent instanceof AbstractBranchAwareProject);
      final AbstractBranchAwareProject parentProject =
          (AbstractBranchAwareProject) parent;
      return parentProject.getSource();
    }
  }

  /**
   * @return whether this project is a delegate of some parent project
   * that dictates information about the {@link Branch} or {@link SCM}.
   */
  private boolean isDelegate() {
    return getScm() instanceof DelegateSCM;
  }

  /**
   * Projects that delegate often have a largely cosmetic workspace,
   * based on which logic/control decisions are made, e.g. how to build
   * up a DSL project from its checked in code.  To minimize the impact
   * of these read-only workspaces in the presence of composition, attempt
   * to share our workspace with a parent job, if it has a workspace on
   * the node on which we have been scheduled.  This should be called from
   * {@code decideWorkspace}.
   * <p>
   * NOTE: public so that it is accessible to builds.
   *
   * @param onNode The Node we've been scheduled on.
   * @return A lease on the workspace to share, or null if no such workspace
   * exists on this node.
   */
  @Nullable
  public WorkspaceList.Lease shareWorkspace(Node onNode) {
    final FilePath workspace = _shareWorkspace(onNode);
    return workspace == null ? null :
        WorkspaceList.Lease.createDummyLease(workspace);
  }


  /** @see #shareWorkspace */
  @Nullable
  private FilePath _shareWorkspace(Node onNode) {
    final AbstractBranchAwareProject container = getSameNodeConstraint();
    if (container == this) {
      return null;
    }

    final FilePath workspace = container.getSomeWorkspace();
    if (workspace == null) {
      // We are inside of an active parent build, there must be a workspace
      return null;
    }

    final Node node = workspaceToNode(workspace);
    if (node == null) {
      // We are inside of an active parent build, there must be a workspace
      return null;
    }

    if (node != onNode) {
      // We were scheduled on a different node, tough luck.
      return null;
    }

    // If we are running on the same node as our parent, then share
    // its workspace.
    return workspace;
  }

  /** {@inheritDoce} */
  @Override
  public AbstractBranchAwareProject getSameNodeConstraint() {
    if (!(this instanceof ReadOnlyWorkspaceTask)) {
      // We can't squat in a parent project's workspace if
      // we plan to mutate things
      return this;
    }

    if (!isDelegate()) {
      // We aren't necessarily executing within the same source context as a
      // parent project.
      return this;
    }

    checkState(getParent() instanceof AbstractBranchAwareProject);
    final AbstractBranchAwareProject container =
        (AbstractBranchAwareProject) getParent();
    if (!(container instanceof ReadOnlyWorkspaceTask)) {
      // The container mutates its workspace.
      return this;
    }
    checkState(container != this, "Avoiding infinite recursion");
    // Try to get ourselves scheduled onto the same node as our
    // parent, so we can share its workspace
    return container.getSameNodeConstraint();
  }

  /** From GitSCM */
  private static Node workspaceToNode(FilePath workspace) {
    Jenkins j = Jenkins.getInstance();
    if (workspace.isRemote()) {
      for (Computer c : j.getComputers()) {
        if (c.getChannel() == workspace.getChannel()) {
          Node n = c.getNode();
          if (n != null) {
            return n;
          }
        }
      }
    }
    return j;
  }

  /** {@inheritDoc} */
  @Override
  public void setScm(SCM scm) throws IOException {
    // Marshall the scm into a branch.
    // NOTE: by passing us a DelegateSCM here, clients and turn us into an
    // "isDelegate()", which we disallow in "setBranch()".  This is why once
    // we have turned the SCM into a Branch, we don't simply call "setBranch()"
    this.branch = new Branch(null, new SCMHead(NAME), checkNotNull(scm),
        ImmutableList.<BranchProperty>of());
    save();  // persist the state change to disk
  }

  /** {@inheritDoc} */
  @Override
  public SCM getScm() {
    // Access and return our actual SCM.  As this is used for UI round-tripping,
    // if we were to return another SCM here, the user would see a snapshot of
    // the parent's SCM instead of this.  The point of DelegateSCM is that we
    // can return it here, even if "isDelegate()" because it will do the right
    // thing.
    return branch.getScm();
  }

  /** The name to give degenerate {@link SCMHead}s */
  private static final String NAME = "name";
}
