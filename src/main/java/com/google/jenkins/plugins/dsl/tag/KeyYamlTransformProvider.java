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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;

import jenkins.model.Jenkins;

/**
 * Register a YamlTransformProvider that composes with all other providers
 * in order to create a version suitable for usage in key contexts.
 *
 * For Example:
 * <pre>
 *   !key:by-name Google Cloud Ephemeral Deployer
 *   !key:freestyle
 * </pre>
 * Should return the same things as:
 * <pre>
 *   !by-name Google Cloud Ephemeral Deployer
 *   !freestyle
 * </pre>
 * However, with '.' characters replaced with '-' characters.
 */
@Extension(ordinal = -1000 /* fallback */)
public class KeyYamlTransformProvider extends YamlTransformProvider {
  private static final Logger logger =
      Logger.getLogger(KeyYamlTransformProvider.class.getName());

  public KeyYamlTransformProvider() {
    this.transforms = new Supplier<List<YamlTransform>>() {
      @Override
      public List<YamlTransform> get() {
        List<YamlTransform> list = Lists.newArrayList();
        for (YamlTransformProvider provider :
                 checkNotNull(Jenkins.getInstance())
                 .getExtensionList(YamlTransformProvider.class)) {
          if (provider == KeyYamlTransformProvider.this) {
            continue;
          }
          for (YamlTransform xform : provider.provide()) {
            list.add(xform);
          }
        }
        return list;
      }
    };
  }

  @VisibleForTesting
  KeyYamlTransformProvider(Supplier<List<YamlTransform>> transforms) {
    this.transforms = checkNotNull(transforms);
  }
  private final Supplier<List<YamlTransform>> transforms;

  /** {@inheritDoc} */
  @Override
  protected List<YamlTransform> provide() {
    return _provide();
  }

  @VisibleForTesting
  List<YamlTransform> _provide() {
    List<YamlTransform> list = Lists.newArrayList();
    for (YamlTransform xform : transforms.get()) {
      list.add(new Transform(xform));
    }
    return list;
  }

  @VisibleForTesting
  static class Transform implements YamlTransform {
    public Transform(YamlTransform xform) {
      this.xform = xform;
    }

    /** {@inheritDoc} */
    @Override
    public String getTag() {
      final String innerTagNoBang = xform.getTag().substring(1);
      return "!key:" + innerTagNoBang;
    }

    /** {@inheritDoc} */
    @Override
    public List<Class> getClasses() {
      return ImmutableList.copyOf(xform.getClasses());
    }

    /** {@inheritDoc} */
    @Override
    public String construct(String value) {
      // Delegate to the inner transform to interpret the remaining argument
      final String inner = xform.construct(value);

      // Replace the result with a suitable JSON key by replacing '.' with '-'
      return inner.replace('.', '-');
    }

    /** {@inheritDoc} */
    @Override
    public String represent(Class clazz) {
      return xform.represent(clazz);
    }
    private final YamlTransform xform;
  }
}