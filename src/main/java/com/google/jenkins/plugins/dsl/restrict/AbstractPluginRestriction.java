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
package com.google.jenkins.plugins.dsl.restrict;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;

import hudson.PluginManager;
import hudson.PluginWrapper;

import jenkins.model.Jenkins;

/** Base class for restrictions based on what plugin a class is from */
public abstract class AbstractPluginRestriction extends AbstractRestriction {
  /** {@inheritDoc} */
  @Override
  public final ClassLoader getClassLoader(RestrictedProject project) {
    final ClassLoader inner = getBaseClassLoader(project);

    return new ClassLoader() {
      @Override
      public Class loadClass(String className) throws ClassNotFoundException {
        final Class clazz = inner.loadClass(className);
        if (shouldFilter(clazz)) {
          throw new RestrictedTypeException(
              Messages.AbstractPluginRestriction_Filtered(clazz.getName()));
        }
        return clazz;
      }
    };
  }

  /**
   * Hook for injecting a mock plugin manager since JenkinsRule does
   * seem to get set up properly.
   */
  @VisibleForTesting
  public PluginManager getPluginManager() {
    return checkNotNull(Jenkins.getInstance()).getPluginManager();
  }

  /** Callback for implementations to determine how plugins are filtered. */
  protected abstract boolean isPluginAllowed(PluginWrapper plugin);

  /** The various plugins on which we could be asked to filter. */
  public static List<PluginWrapper> getPlugins() {
    return checkNotNull(Jenkins.getInstance()).getPluginManager().getPlugins();
  }

  /** Trampoline method that determines the Plugin for the class and delgates */
  private boolean shouldFilter(Class clazz) {
    final PluginWrapper wrapper = getPluginManager().whichPlugin(clazz);

    if (wrapper == null) {
      return false;
    }

    return !isPluginAllowed(wrapper);
  }
}
