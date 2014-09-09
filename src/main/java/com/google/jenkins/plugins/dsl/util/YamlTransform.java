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
package com.google.jenkins.plugins.dsl.util;

import java.util.List;

/**
 * This interface describes a transformation we would like to
 * use tags to affect in the Yaml &lt;--&gt; translation process.
 * <p>
 * One anticipated use case of this will be to allow users to adopt
 * a shorthand to avoid learning fully qualified class named for plugins:
 * <pre>
 *   kind: hudson.model.FreeStyleProject   # &lt;== {@link #getClazz()}
 * </pre>
 * might become:
 * <pre>
 *   kind: !freestyle                      # &lt;== {@link #getTag()}
 * </pre>
 *
 * However, we might also want to have a common lookup tag
 * <pre>
 *   kind: !name "Invoke top-level Maven targets"
 *   #           ^------------------------------^
 *   # We expect the above string will be returned by {@link #represent(Class)},
 *   # similarly we expect it to be the constant input to
 *   # {@link #construct(String)}.
 * </pre>
 * to allow a lookup by display name, to resolve:
 * <pre>
 *   kind: hudson.tasks.Maven
 * </pre>
 */
public interface YamlTransform {
  /** @return the {@code "!foo"} literal we expect to appear in YAML */
  String getTag();

  /** @return the set of classes for which we are exposing a shorthand */
  List<Class> getClasses();

  /**
   * This takes the argument supplied to the tag (e.g. {@code !foo bar})
   * and determines to what class that translates.
   * <p>
   * NOTE: This is the dual of {@link #represent(Class)}
   */
  String construct(String value);

  /**
   * This take the class we are restoring to Yaml and determines the appropriate
   * argument for our tag.
   * <p>
   * NOTE: This is the dual of {@link #construct(String)}
   */
  String represent(Class clazz);
}
