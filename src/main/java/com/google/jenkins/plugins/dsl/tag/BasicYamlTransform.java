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

import com.google.common.collect.ImmutableList;

/**
 * This implements a simple, (possibly) argumentless tag to
 * class {@link com.google.jenkins.plugins.dsl.util.YamlTransform}.
 */
public class BasicYamlTransform extends ArgumentYamlTransform {
  public BasicYamlTransform(String tag, Class type) {
    this(tag, "", type);
  }

  public BasicYamlTransform(String tag, String arg, Class type) {
    super(tag, ImmutableList.of(arg), ImmutableList.of(type));
  }
}