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

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractItem;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;

import jenkins.model.Jenkins;

/** ExtensionPoint for surfacing restrictions on what a DSL job can do. */
public abstract class AbstractRestriction
    implements Describable<AbstractRestriction>, ExtensionPoint {
  /**
   * The primary interface of this {@code ExtensionPoint}.
   *
   * Implementations may return a class loader that limits the set of classes
   * made available for instantiation of the DSL objects.
   */
  public abstract ClassLoader getClassLoader(RestrictedProject project);

  /**
   * @return the {@link ClassLoader} on top of which to layer our restrictions.
   */
  protected final ClassLoader getBaseClassLoader(RestrictedProject project) {
    // Search the project's parent hierarchy for additional
    // restricted projects on which to base our restrictions.
    ItemGroup parent = project.asProject().getParent();
    while (parent != null) {
      if (parent instanceof RestrictedProject) {
        final RestrictedProject parentProject = (RestrictedProject) parent;
        return parentProject.getRestriction().getClassLoader(parentProject);
      }
      if (!(parent instanceof AbstractItem)) {
        break;
      }
      parent = ((AbstractItem) parent).getParent();
    }

    // If no parents of this are restricted, then base things on the
    // unrestricted uber class loader.
    // NOTE: we wrap this class loader anyways to surface diagnostics
    // when there are actual class loading failures, so we can surface
    // them to the user.
    final ClassLoader base =
        checkNotNull(Jenkins.getInstance()).getPluginManager().uberClassLoader;
    return new ClassLoader() {
      @Override
      public Class loadClass(String name) throws ClassNotFoundException {
        try {
          return base.loadClass(name);
        } catch (ClassNotFoundException e) {
          throw new BadTypeException(
              Messages.AbstractRestriction_ClassNotFound(name));
        }
      }
    };
  }

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public static DescriptorExtensionList<AbstractRestriction,
      Descriptor<AbstractRestriction>> all() {
    return checkNotNull(Jenkins.getInstance()).<AbstractRestriction,
        Descriptor<AbstractRestriction>>getDescriptorList(
            AbstractRestriction.class);
  }

  /**
   * Boilerplate, see:
   * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
   */
  public Descriptor<AbstractRestriction> getDescriptor() {
    return (Descriptor<AbstractRestriction>) checkNotNull(Jenkins.getInstance())
        .getDescriptor(getClass());
  }
}
