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

import com.google.jenkins.plugins.dsl.restrict.RestrictedProject;
import com.google.jenkins.plugins.dsl.tag.YamlTransformProvider;
import com.google.jenkins.plugins.dsl.util.Binder;
import com.google.jenkins.plugins.dsl.util.YamlToJson;

/** Module for providing the dependencies from {@link YamlProject} */
public class YamlModule {
  /** @return the {@link Binder} used for producing projects from JSON */
  public Binder getBinder(RestrictedProject project) {
    return new Binder.Default(project.getRestriction().getClassLoader(project));
  }

  /** @return the {@link YamlToJson} for translating the DSL to JSON */
  public YamlToJson getYamlToJson() {
    return new YamlToJson.Default(YamlTransformProvider.get());
  }
}