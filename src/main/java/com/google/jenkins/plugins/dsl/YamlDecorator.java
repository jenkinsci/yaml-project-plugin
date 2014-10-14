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
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Strings;
import com.google.jenkins.plugins.dsl.tag.YamlTransformProvider;
import com.google.jenkins.plugins.dsl.util.Filter;
import com.google.jenkins.plugins.dsl.util.JsonToYaml;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

import net.sf.json.JSONObject;

/**
 * This listens for changes to projects through the Jenkins User Interface.
 * When a project is changed, it captures the submitted form data, converting
 * it into an approximation of the DSL we expect the user would want to use
 * with {@link YamlProject}.
 *
 * TODO(mattmoor): We want this to work for Views as well, to we will likely
 * have to change to a SaveableListener.
 */
@Extension
public class YamlDecorator extends ItemListener {
  private static Logger logger = Logger.getLogger(
      YamlDecorator.class.getName());

  /** {@inheritDoc} */
  @Override
  public void onUpdated(Item item) {
    if (!(item instanceof AbstractProject)) {
      return;
    }

    final StaplerRequest request = Stapler.getCurrentRequest();
    if (request == null) {
      return;
    }

    JSONObject json;
    try {
      // Avoid the logic to throw and redirect to an error page
      // when "getSubmittedForm" is called with no form data.
      if (Strings.isNullOrEmpty(request.getParameter("json"))) {
        return;
      }
      json = request.getSubmittedForm();
      if (json == null) {
        return;
      }
      // Scrub the object of a lot of the fluff that comes through
      // as part of the form submission.
      // TODO(mattmoor): Scrub the json (based on kind?)
      json = Filter.object(json);
    } catch (ServletException e) {
      logger.log(SEVERE, e.getMessage(), e);
      return;
    }
    final AbstractProject project = (AbstractProject) item;
    final JsonToYaml j2y = new JsonToYaml.Default(YamlTransformProvider.get());
    try {
      final YamlAction action = YamlAction.of(project);

      // The kind is used to recover the project type
      json.put("kind", item.getClass().getName());
      // The name is specified by the container YamlProject, but this is
      // too generic a term to filter out above.
      json.put("name", null);

      action.setYaml(j2y.toYaml(json.toString()));
      project.save();
    } catch (IOException e) {
      logger.log(SEVERE, e.getMessage(), e);
    }
  }
}