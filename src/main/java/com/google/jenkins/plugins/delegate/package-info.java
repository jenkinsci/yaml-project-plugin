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

/**
 * This package provides some abstract classes intended to hoist some of the
 * boilerplate associated with creating a job that contains other jobs, and
 * as part of its own execution delegates to those jobs.
 * <p>
 * In support of this, this package provides a {@link DelegateSCM} that aims to
 * provide the functionality needed to have an inner job inherit the SCM of its
 * container job, and ensure the inner jobs execution occurs at the same
 * changeset as its container job's execution.
 */
package com.google.jenkins.plugins.delegate;