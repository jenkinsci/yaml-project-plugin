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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

/**
 * This is used to implement simple two-part tags where a
 * common tag is disambiguated with an argument, e.g.
 * <ul>
 *  <li><pre><code>!column weather</code></pre>
 *  <li><pre><code>!column status</code></pre>
 *  <li><pre><code>!git scm</code></pre>
 *  <li><pre><code>!git publisher</code></pre>
 * </ul>
 */
public class ArgumentYamlTransform implements YamlTransform {
  public ArgumentYamlTransform(String tag, List<String> arguments,
      List<Class> types) {
    checkState(arguments.size() == types.size());
    this.tag = checkNotNull(tag);
    this.arguments = ImmutableList.copyOf(arguments);
    this.types = ImmutableList.copyOf(types);
  }

  /** {@inheritDoc} */
  @Override
  public String getTag() {
    return tag;
  }

  /** {@inheritDoc} */
  @Override
  public List<Class> getClasses() {
    return types;
  }

  /** @return our list of arguments */
  public List<String> getArguments() {
    return arguments;
  }

  /** {@inheritDoc} */
  @Override
  public String construct(String value) {
    final int indexof = arguments.indexOf(value);
    checkState(indexof != -1);
    return types.get(indexof).getName();
  }

  /** {@inheritDoc} */
  @Override
  public String represent(Class clazz) {
    final int indexof = types.indexOf(clazz);
    checkState(indexof != -1);
    return arguments.get(indexof);
  }

  /** Merge the two transforms, producing a new combined transformation. */
  public ArgumentYamlTransform merge(ArgumentYamlTransform other) {
    checkState(getTag().equals(other.getTag()));
    return new ArgumentYamlTransform(getTag(),
        Lists.newArrayList(
            Iterables.concat(getArguments(), other.getArguments())),
        Lists.newArrayList(Iterables.concat(getClasses(), other.getClasses())));
  }

  private final String tag;
  private final List<Class> types;
  private final List<String> arguments;
}