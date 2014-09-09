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

import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.InvisibleAction;

/**
 * This {@link InvisibleAction} is attached to our {@link YamlBuild}s
 * to record what nested {@link AbstractProject}'s {@link AbstractBuild}
 * was executed for later recovery.
 */
public class YamlHistoryAction extends InvisibleAction {
  public YamlHistoryAction(String projectName, int buildNumber) {
    this.projectName = checkNotNull(projectName);
    this.buildNumber = buildNumber;
  }

  /**
   * Fetch the nested project to which our owning {@link YamlBuild}
   * delegated execution.
   */
  public AbstractProject getProject(YamlProject<?> project) {
    return project.getItem(projectName);
  }

  /** Fetch the nested project's build recording our delegated execution */
  public AbstractBuild getBuild(YamlProject<?> project) {
    return getProject(project).getBuildByNumber(buildNumber);
  }

  /**
   * Search the actions of the {@link YamlBuild} for a {@link YamlHistoryAction}
   *
   * @return the action, or null if not found (or no build was passed)
   */
  @Nullable
  public static YamlHistoryAction of(YamlBuild build) {
    if (build == null) {
      return null;
    }
    try {
      return (YamlHistoryAction) Iterables.find(build.getRawActions(),
          Predicates.instanceOf(YamlHistoryAction.class));
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  private final String projectName;
  private final int buildNumber;
}