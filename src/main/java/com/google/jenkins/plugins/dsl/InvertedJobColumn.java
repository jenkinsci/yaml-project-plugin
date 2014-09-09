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

import hudson.Extension;
import hudson.views.JobColumn;

/**
 * Trivial extension of {@link JobColumn} that inverts the initial sort order
 */
public class InvertedJobColumn extends JobColumn {
  /** Boilerplate extension code */
  @Extension
  public static class DescriptorImpl extends JobColumn.DescriptorImpl {
    /** {@inheritDoc} */
    @Override
    public boolean shownByDefault() {
      // Don't add this to list views by default.
      return false;
    }

    /** The name to display on our column */
    public String getColumnName() {
      // Used in place of display name in our jelly
      return super.getDisplayName();
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.InvertedJobColumn_DisplayName();
    }
  }
}