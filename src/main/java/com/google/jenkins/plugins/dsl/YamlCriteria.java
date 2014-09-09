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

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.model.TaskListener;

import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceCriteria.Probe;

/**
 * This implements the criteria by which the {@link YamlMultiBranchProject}
 * will determine which branches for which to create projects.
 */
public class YamlCriteria implements SCMSourceCriteria {
  public YamlCriteria(String yamlPath) {
    this.yamlPath = checkNotNull(yamlPath);
  }

  /** Simple accessor for the yaml path with which this was instantiated. */
  public String getYamlPath() {
    return yamlPath;
  }
  private final String yamlPath;

  /** {@inheritDoc} */
  @Override
  public boolean isHead(Probe probe, TaskListener listener)
      throws IOException {
    if (probe.exists(getYamlPath())) {
      return true;
    }

    listener.getLogger().println(
        Messages.YamlMultiBranchProject_MissingFile(getYamlPath()));
    return false;
  }
}
