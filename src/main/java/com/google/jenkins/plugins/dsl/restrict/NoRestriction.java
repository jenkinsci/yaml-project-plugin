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

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;

/** Simple unrestricted implementation */
public class NoRestriction extends AbstractRestriction {
  @DataBoundConstructor
  public NoRestriction() { }

  /** {@inheritDoc} */
  @Override
  public ClassLoader getClassLoader(RestrictedProject project) {
    return getBaseClassLoader(project);
  }

  /** Boilerplate extension code */
  @Extension(ordinal = 1000.0)
  public static class DescriptorImpl extends Descriptor<AbstractRestriction> {
    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.NoRestriction_DisplayName();
    }
  }
}
