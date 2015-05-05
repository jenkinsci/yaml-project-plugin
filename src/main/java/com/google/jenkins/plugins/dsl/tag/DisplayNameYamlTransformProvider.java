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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.model.Descriptor;

import jenkins.model.Jenkins;

/**
 * Register a global {@code !by-name} tag that takes as a single argument
 * the display name of the plugin we want to instantiate.  For example:
 * <pre>
 *   $class: !by-name Google Cloud Deployer
 * </pre>
 */
@Extension(ordinal = -1000 /* fallback */)
public class DisplayNameYamlTransformProvider
    extends YamlTransformProvider {
  private static final Logger logger =
      Logger.getLogger(DisplayNameYamlTransformProvider.class.getName());

  public DisplayNameYamlTransformProvider() {
    this(new Supplier<List<Descriptor>>() {
          @Override
          public List<Descriptor> get() {
            List<Descriptor> descriptors = Lists.newArrayList();
            ExtensionList<Descriptor> extensions =
                checkNotNull(Jenkins.getInstance())
                .getExtensionList(Descriptor.class);
            for (ExtensionComponent<Descriptor> component :
                     extensions.getComponents()) {
              descriptors.add(component.getInstance());
            }
            return descriptors;
          }
        });
  }

  @VisibleForTesting
  DisplayNameYamlTransformProvider(Supplier<List<Descriptor>> descriptors) {
    this.descriptors = checkNotNull(descriptors);
  }
  private final Supplier<List<Descriptor>> descriptors;

  /** {@inheritDoc} */
  @Override
  protected List<YamlTransform> provide() {
    return _provide();
  }

  @VisibleForTesting List<YamlTransform> _provide() {
    return ImmutableList.<YamlTransform>of(
        new ByNameTransform(descriptors.get()));
  }

  private static class ByNameTransform implements YamlTransform {
    public ByNameTransform(List<Descriptor> descriptors) {
      final Map<String, Class> byName = Maps.newHashMap();
      final Map<Class, String> byClass = Maps.newHashMap();

      for (Descriptor descriptor : descriptors) {
        final Class clazz = descriptor.clazz;
        final String displayName = descriptor.getDisplayName();

        // The first time we see it, add it to the byName map
        if (!byName.containsKey(displayName)) {
          byName.put(displayName, clazz);
          byClass.put(clazz, displayName);
        } else {
          // Subsequent visitations, replace the byName entry
          // with a sentinel value and remove the original Class
          // from the map.
          byClass.remove(byName.get(displayName));
          byName.put(displayName, COLLISION_SENTINEL);
        }
      }

      // Assign as immutable entities to our fields.
      this.byName = Collections.unmodifiableMap(byName);
      this.byClass = Collections.unmodifiableMap(byClass);
    }

    /** {@inheritDoc} */
    @Override
    public String getTag() {
      return "!by-name";
    }

    /** {@inheritDoc} */
    @Override
    public List<Class> getClasses() {
      return ImmutableList.copyOf(byClass.keySet());
    }

    /** {@inheritDoc} */
    @Override
    public String construct(String value) {
      checkState(byName.containsKey(value),
          Messages.DisplayNameYamlTransformProvider_BadDisplayName(value));

      final Class result = byName.get(value);
      checkState(!COLLISION_SENTINEL.equals(result),
          Messages.DisplayNameYamlTransformProvider_CommonName(value));
      return result.getName();
    }
    private final Map<String, Class> byName;

    /** {@inheritDoc} */
    @Override
    public String represent(Class clazz) {
      checkState(byClass.containsKey(clazz),
          Messages.DisplayNameYamlTransformProvider_BadClass(
              clazz.getName()));

      return byClass.get(clazz);
    }
    private final Map<Class, String> byClass;

    /** Not intended to ever be surfaced. */
    private static final Class COLLISION_SENTINEL = Object.class;
  }
}
