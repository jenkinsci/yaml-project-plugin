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

import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import jenkins.model.Jenkins;

/**
 * This {@link ExtensionPoint} serves as a means for plugins to augment
 * the {@link YamlTransform} discovery process.  The intended usage is:
 * <pre><code>
 *   List&lt;YamlTransform&gt; list = YamlTransformProvider.get();
 * </code></pre>
 * This will delegate to the various extension implementations to
 * {@link #provide()} a {@link List} of requirements from things it
 * understands how to discover.
 */
public abstract class YamlTransformProvider implements ExtensionPoint {
  private static final Logger logger =
      Logger.getLogger(YamlTransformProvider.class.getName());

  /**
   * This hook is intended for providers to implement such that they can
   * surface custom transformations.
   */
  protected abstract List<YamlTransform> provide();

  /**
   * The the entrypoint for {@link YamlTransform} gathering, this static method
   * delegates to all registered providers to provide their set of discoverable
   * {@link YamlTransform}s.
   */
  public static List<YamlTransform> get() {
    ExtensionList<YamlTransformProvider> providers =
        checkNotNull(Jenkins.getInstance()).getExtensionList(
            YamlTransformProvider.class);

    List<YamlTransform> result = Lists.newArrayList();
    for (YamlTransformProvider provider : providers) {
      result.addAll(provider.provide());
    }
    return result;
  }
}