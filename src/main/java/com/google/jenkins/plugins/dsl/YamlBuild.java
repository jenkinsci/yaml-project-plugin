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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.kohsuke.stapler.framework.io.LargeText;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.ByteStreams.copy;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedLongs;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.jenkins.plugins.delegate.DelegateSCM;
import com.google.jenkins.plugins.dsl.util.Binder;

import hudson.FilePath;
import hudson.console.ModelHyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.scm.NullSCM;
import hudson.slaves.WorkspaceList;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevisionAction;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * This records the execution of a {@link YamlProject}.
 * @param <T> The type of element contained
 * @see YamlExecution
 */
public class YamlBuild<T extends AbstractProject & TopLevelItem>
    extends AbstractBuild<YamlProject<T>, YamlBuild<T>> {
  /** Instantiate the build */
  public YamlBuild(YamlProject<T> parent) throws IOException {
    super(parent);
  }

  /** Used to load the build from disk */
  public YamlBuild(YamlProject<T> parent, File file) throws IOException {
    super(parent, file);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    execute(new YamlExecution());
  }

  /** {@inheritDoc} */
  @Override
  public List<Action> getActions() {
    YamlHistoryAction action = YamlHistoryAction.of(this);
    if (action == null) {
      return getRawActions();
    }

    List<Action> actions = Lists.newArrayList();

    final AbstractBuild build = action.getBuild(getParent());
    if (build == null) {
      return getRawActions();
    }

    // Delegate to the nested build.
    for (Action a : build.getActions()) {
      if (preservedAction(a)) {
        // Exclude actions from the child that will be preserved
        // from the parent.
        continue;
      }
      actions.add(a);
    }

    // Add in our preserved actions.
    for (Action a : getRawActions()) {
      if (preservedAction(a)) {
        actions.add(a);
      }
    }

    return actions;
  }

  /**
   * @return whether the given action type should be preserved from our build,
   * vs. extracted from our delegate build.
   */
  private static boolean preservedAction(Action a) {
    return SCMRevisionAction.class.isInstance(a)
        || CauseAction.class.isInstance(a);
  }

  /** Get the actual actions of this build, without delegation */
  public List<Action> getRawActions() {
    return super.getActions();
  }

  /**
   * This performs the actual execution of a {@link YamlProject}.
   * After the workspace has been set up, this loads the DSL file,
   * and instantiates a project from it.  It then schedules that
   * project and follows its execution as-if it were our own.
   */
  protected class YamlExecution extends AbstractBuild.AbstractRunner {
    /** {@inheritDoc} */
    @Override
    protected Result doRun(BuildListener listener)
        throws IOException, InterruptedException {
      final FilePath ws = checkNotNull(getWorkspace());
      final YamlProject<T> parent = YamlBuild.this.getParent();

      // TODO(mattmoor): Resolve variables in the yaml path?
      final FilePath yamlFile = ws.child(parent.getYamlPath());

      if (!yamlFile.exists()) {
        listener.error(
            Messages.YamlBuild_MissingFile(parent.getYamlPath()));
        return Result.FAILURE;
      }

      // Write the Yaml file to the log for now.
      copy(yamlFile.read(), maybeLog(listener,
              Messages.YamlBuild_LoadedYaml()));
      maybeLog(listener, "\n\n");

      // TODO(mattmoor): Catch pertinent exceptions to report malformed
      // Yaml.
      final JSONObject json = readToJSON(yamlFile);
      maybeLog(listener, Messages.YamlBuild_LoadedJson());
      maybeLog(listener, json.toString());

      final AbstractProject project = getOrCreateProject(json);

      maybeLog(listener, Messages.YamlBuild_CreatedJob(
          ModelHyperlinkNote.encodeTo(project, project.getName())));

      ParametersAction parameters =
          YamlBuild.this.getAction(ParametersAction.class);
      if (parameters == null) {
        parameters = getDefaultParametersValues(project);
      }

      final CauseAction cause = new CauseAction(
          new Cause.UpstreamCause(YamlBuild.this));

      final Queue.Item item = Queue.getInstance().schedule(
          project, 0, cause, parameters);
      if (item == null) {
        throw new IllegalStateException("Project not scheduled");
      }

      listener.getLogger().println(
          Messages.YamlBuild_StartDelimiter(parent.getYamlPath()));
      try {
        AbstractBuild newBuild = null;
        do {
          try {
            // This future waits for completion, we only need it to have
            // started.  This future also doesn't seem to properly report
            // cancellation.
            newBuild = (AbstractBuild) item.getFuture().get(1, SECONDS);
          } catch (CancellationException e) {
            return Result.ABORTED;
          } catch (TimeoutException e) {
            // Check intermittently for cancellation
            final Queue.Item currentItem = Queue.getInstance().getItem(item.id);
            if (currentItem instanceof Queue.LeftItem) {
              final Queue.LeftItem leftItem = (Queue.LeftItem) currentItem;
              if (leftItem.isCancelled()) {
                return Result.ABORTED;
              }

              final Queue.Executable executable = leftItem.getExecutable();
              if (executable instanceof AbstractBuild) {
                newBuild = (AbstractBuild) executable;
              }
            }
          }
        } while (newBuild == null);

        // Attach an action so that we know what sub-project and sub-build
        // were executed as part of this build.
        YamlBuild.this.addAction(new YamlHistoryAction(
            project.getName(), newBuild.getNumber()));

        writeWholeLogTo(newBuild, listener.getLogger());

        listener.getLogger().println(
            Messages.YamlBuild_EndDelimiter(parent.getYamlPath()));
        return newBuild.getResult();
      } catch (ExecutionException e) {
        e.printStackTrace(listener.error(
            Messages.YamlBuild_InnerException()));
        return Result.FAILURE;
      }
    }

    /** {@inheritDoc} */
    @Override
    protected WorkspaceList.Lease decideWorkspace(Node n, WorkspaceList wsl)
        throws InterruptedException, IOException {
      final YamlProject project = YamlBuild.this.getParent();
      final WorkspaceList.Lease lease = project.shareWorkspace(n);
      return (lease != null) ? lease : super.decideWorkspace(n, wsl);
    }

    /** Get the default parameter values for the given delegate */
    private ParametersAction getDefaultParametersValues(
        AbstractProject delegate) {
      final ParametersDefinitionProperty property =
          (ParametersDefinitionProperty) delegate.getProperty(
              ParametersDefinitionProperty.class);
      if (property == null) {
        return null;
      }

      final List<ParameterValue> result = Lists.newArrayList();
      for (ParameterDefinition def : property.getParameterDefinitions()) {
        ParameterValue value = def.getDefaultParameterValue();
        if (value != null) {
          result.add(value);
        }
      }

      return new ParametersAction(result);
    }

    /**
     * Pipe the log of the delegated execution through to our log.
     *
     * Modeled after Jenkins' Run's writeWholeLogTo, which is not giving us
     * annotations back.  From the javadoc:
     * "If someone is still writing to the log, this method will not
     * return until the whole log file gets written out."
     */
    private void writeWholeLogTo(AbstractBuild build, OutputStream out)
        throws IOException {
      long pos = 0;

      while (!build.getLogFile().exists()
          || build.getLogFile().isDirectory()) {
        Uninterruptibles.sleepUninterruptibly(1, SECONDS);
      }

      do {
        LargeText logText = new LargeText(build.getLogFile(),
            build.getCharset(), !build.isLogUpdated());
        pos = logText.writeLogTo(pos, out);
        if (logText.isComplete()) {
          break;
        }
        Uninterruptibles.sleepUninterruptibly(1, SECONDS);
      } while (true);
    }

    /**
     * This method is used to log messages that should only show up in
     * verbose logs.  It handles check the global flag and logging the
     * message if it should be logged.  It also returns a stream through
     * which continued verbose logging may be written, which similarly will
     * only show up if verbose logging is enabled.
     */
    private PrintStream maybeLog(BuildListener listener, String message) {
      if (YamlBuild.this.getParent().getDescriptor().isVerbose()) {
        listener.getLogger().println(message);
        return listener.getLogger();
      } else {
        return BuildListener.NULL.getLogger();
      }
    }

    /**
     * Determine whether a project exists for the json loaded from the DSL file.
     */
    private AbstractProject getOrCreateProject(JSONObject json)
        throws IOException {
      final String jsonText = json.toString();
      final String hash = UnsignedLongs.toString(
          Hashing.md5().hashString(jsonText, Charsets.UTF_8).asLong(), 16);

      final YamlProject<T> parent = YamlBuild.this.getParent();
      final YamlHistoryAction action =
          YamlHistoryAction.of(YamlBuild.this.getPreviousBuild());
      // First build, there is no project to re-use
      if (action == null) {
        return newProject(json, hash);
      }
      final AbstractProject lastProject = action.getProject(parent);
      // If the last project had the same hash, then simply re-use it
      if (lastProject.getName().endsWith(hash)) {
        return lastProject;
      }

      // If we aren't using the lastProject then we need to blow away its
      // workspace and that of any of its descendants.
      deleteWorkspaceRecursive(lastProject);

      // Otherwise create a new one.
      return newProject(json, hash);
    }

    /** Instantiate a new project from the json loaded from the DSL file */
    private AbstractProject newProject(JSONObject json, String hash)
        throws IOException {
      final YamlProject<T> parent = YamlBuild.this.getParent();
      final String displayName =
          String.format("v%04d", parent.getItems().size());
      final String jobName = String.format("%s-%s", displayName, hash);
      final Binder binder = parent.getModule().getBinder(parent);
      final T project = (T) binder.bindJob(parent, jobName, json);
      project.setDisplayName(displayName);

      // Validate that the embedded project doesn't specify source control,
      // and instate our own DelegateSCM to inject our SCM into it.
      checkState(project.getScm() instanceof NullSCM,
          Messages.YamlBuild_DSLWithSCMError());
      project.setScm(new DelegateSCM(YamlProject.class));

      project.onCreatedFromScratch();
      parent.addItem(project);
      project.save();
      ItemListener.fireOnCreated(project);

      checkNotNull(Jenkins.getInstance()).rebuildDependencyGraph();

      return project;
    }

    private void deleteWorkspaceRecursive(AbstractProject project)
        throws IOException {
      final AbstractBuild build = project.getLastBuild();
      if (build != null) {
        final FilePath workspace = build.getWorkspace();
        if (workspace != null) {
          try {
            workspace.deleteRecursive();
          } catch (InterruptedException e) {
            throw new IOException(e);
          }
        }
      }

      if (project instanceof ItemGroup) {
        deleteWorkspacesRecursive((ItemGroup) project);
      }
    }

    private void deleteWorkspacesRecursive(ItemGroup<? extends Item> itemGroup)
        throws IOException {
      for (Item item : itemGroup.getItems()) {
        if (item instanceof AbstractProject) {
          deleteWorkspaceRecursive((AbstractProject) item);
        } else if (item instanceof ItemGroup) {
          deleteWorkspacesRecursive((ItemGroup) item);
        }
      }
    }

    /** Read the DSL file into a {@link JSONObject}. */
    private JSONObject readToJSON(FilePath yamlFile) throws IOException {
      final String freshJson =
          getParent().getModule().getYamlToJson().toJson(yamlFile.read());
      return (JSONObject) JSONSerializer.toJSON(freshJson);
    }

    /** {@inheritDoc} */
    @Override
    public void post2(BuildListener listener)
        throws IOException, InterruptedException {
      // See: http://javadoc.jenkins-ci.org/hudson/model/    \
      //   AbstractBuild.AbstractBuildExecution.html
      performAllBuildSteps(listener, getParent().getPublishersList(),
          true /* post-build processing */);
      // TODO(mattmoor): Why not: super.post2(listener)?
    }

    /** {@inheritDoc} */
    @Override
    public void cleanUp(BuildListener listener) throws Exception {
      // See: http://javadoc.jenkins-ci.org/hudson/model/    \
      //   AbstractBuild.AbstractBuildExecution.html
      performAllBuildSteps(listener, getParent().getPublishersList(),
          false /* run after finalized processing */);
      super.cleanUp(listener);
    }
  }
}
