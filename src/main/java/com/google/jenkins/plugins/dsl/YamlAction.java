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
import java.util.Collection;

import javax.annotation.Nullable;

import org.kohsuke.stapler.StaplerRequest;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

import net.sf.json.JSONObject;

/**
 * This visible action is attached to projects to present the user with
 * the would-be Yaml DSL for the project, which they could choose to check in
 * and use with {@link YamlProject}.
 */
public class YamlAction implements Action {
  /**
   * @param parent the parent object of this action.
   * @see #getParent().
   */
  YamlAction(AbstractProject parent) {
    this.parent = checkNotNull(parent);
  }

  /** {@inheritDoc} */
  @Override
  public String getIconFileName() {
    return "/plugin/yaml-project/images/24x24/yaml.png";
  }

  /** {@inheritDoc} */
  @Override
  public String getDisplayName() {
    return Messages.YamlAction_DisplayName();
  }

  /** {@inheritDoc} */
  @Override
  public String getUrlName() {
    // Stapler will match this URL name to our action page
    return "asYaml";
  }

  /** The Yaml DSL for the parent project. */
  @Nullable
  public String getYaml() {
    return yaml;
  }

  /** Assigns the Yaml DSL to present to the user. */
  public void setYaml(String yaml) {
    this.yaml = checkNotNull(yaml);
  }

  @Nullable
  private String yaml;

  /**
   * @return the parent object of this action. For a build action,
   *         this is the containing build. For a project action, this is the
   *         containing project.
   */
  public AbstractProject getParent() {
    return parent;
  }

  /** @see #getParent() */
  private final AbstractProject parent;

  /**
   * This handles fetching/attaching a {@link YamlAction} to the given
   * {@link AbstractProject}.  Since the project may have an
   * immutable action list (e.g. {@link hudson.model.FreeStyleProject}),
   * this will attach a {@link JobProperty} to the project that
   * surfaces our {@link Action} when asked.
   */
  public static synchronized YamlAction of(
      AbstractProject project) throws IOException {
    YamlProperty property =
        (YamlProperty) project.getProperty(YamlProperty.class);
    if (property != null) {
      return property.getAction();
    }
    YamlAction yaml = new YamlAction(project);
    project.addProperty(new YamlProperty(yaml));

    return yaml;
  }

  /**
   * This property is attached to projects in order to surface
   * our {@link YamlAction} that presents the user with the DSL
   * for their project.
   */
  private static class YamlProperty extends JobProperty<AbstractProject<?, ?>> {
    public YamlProperty(YamlAction action) {
      this.action = checkNotNull(action);
    }

    /** Surface our embedded {@link YamlAction} */
    public YamlAction getAction() {
      return action;
    }
    private final YamlAction action;

    /** {@inheritDoc} */
    @Override
    public Collection<Action> getJobActions(AbstractProject project) {
      return ImmutableList.<Action>of(getAction());
    }

    /** Boilerplate extension code */
    @Extension
    public static class PropertyDescriptor extends JobPropertyDescriptor {
      /** {@inheritDoc} */
      @Override
      public String getDisplayName() {
        return "You should not be seeing this...";
      }

      /** {@inheritDoc} */
      @Override
      public YamlProperty newInstance(
          StaplerRequest request, JSONObject formData) {
        // This is never populated via the form.
        return null;
      }
    }
  }
}