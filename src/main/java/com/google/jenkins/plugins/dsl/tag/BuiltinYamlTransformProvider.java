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
package com.google.jenkins.plugins.dsl.tag;

import java.util.List;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

import au.com.centrumsystems.hudson.plugin.buildpipeline.BuildPipelineView;
import au.com.centrumsystems.hudson.plugin.buildpipeline.DownstreamProjectGridBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;
import hudson.model.FreeStyleProject;

import jenkins.model.Jenkins;

/**
 * Provide our tag shorthand for core types, as well as de facto
 * standard plugins.
 */
@Extension
public class BuiltinYamlTransformProvider
    extends YamlTransformProvider {
  private static final Logger logger =
      Logger.getLogger(BuiltinYamlTransformProvider.class.getName());

  /** {@inheritDoc} */
  @Override
  protected List<YamlTransform> provide() {
    List<YamlTransform> xforms = Lists.newArrayList();

    // Alphabetically, the list of builtin types for which we want to install
    // tags for.
    xforms.addAll(ImmutableList.of(
        new BasicYamlTransform("!freestyle", FreeStyleProject.class),
        new BasicYamlTransform("!list-view", hudson.model.ListView.class),
        new BasicYamlTransform("!matrix", hudson.matrix.MatrixProject.class),
        new BasicYamlTransform("!maven", hudson.tasks.Maven.class),
        new BasicYamlTransform("!shell", hudson.tasks.Shell.class),
        new BasicYamlTransform("!text-axis", hudson.matrix.TextAxis.class),
        new BasicYamlTransform("!trigger", hudson.tasks.BuildTrigger.class)));

    // ListView columns (alphabetically by argument)
    xforms.addAll(ImmutableList.of(
        new ArgumentYamlTransform("!column",
            ImmutableList.of(
                "build",         // These pair with the classes below,
                "job",           // so order matters.
                "last-duration",
                "last-failure",
                "last-stable",
                "last-success",
                "name",
                "status",
                "weather"),
            ImmutableList.<Class>of(
                hudson.views.BuildButtonColumn.class,
                hudson.views.JobColumn.class,
                hudson.views.LastDurationColumn.class,
                hudson.views.LastFailureColumn.class,
                hudson.views.LastStableColumn.class,
                hudson.views.LastSuccessColumn.class,
                hudson.views.JobColumn.class,
                hudson.views.StatusColumn.class,
                hudson.views.WeatherColumn.class))));

    // Alias the plugins that we use that have !by-name collisions.
    if (checkNotNull(Jenkins.getInstance()).getPlugin("cobertura") != null) {
      xforms.add(new BasicYamlTransform("!cobertura",
              hudson.plugins.cobertura.CoberturaPublisher.class));
    }
    if (checkNotNull(Jenkins.getInstance()).getPlugin("gradle") != null) {
      xforms.add(new BasicYamlTransform("!gradle",
              hudson.plugins.gradle.Gradle.class));
    }
    if (checkNotNull(Jenkins.getInstance()).getPlugin("checkstyle") != null) {
      xforms.add(new BasicYamlTransform("!checkstyle",
              hudson.plugins.checkstyle.CheckStylePublisher.class));
    }
    if (checkNotNull(Jenkins.getInstance()).getPlugin("findbugs") != null) {
      xforms.add(new BasicYamlTransform("!findbugs",
              hudson.plugins.findbugs.FindBugsPublisher.class));
    }
    if (checkNotNull(Jenkins.getInstance())
        .getPlugin("build-pipeline-plugin") != null) {
      xforms.add(
        new ArgumentYamlTransform("!build-pipeline",
            ImmutableList.of(
                "", // no arguments means the view itself
                "downstream grid builder"),
            ImmutableList.<Class>of(
                BuildPipelineView.class,
                DownstreamProjectGridBuilder.class)));
    }

    return xforms;
  }
}