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
 * This package implements a set of restrictions on what types may be loaded
 * as part of DSL instantiation.
 * <p>
 * To implement your own see {@link AbstractRestriction}.
 * <p>
 * We provide default implementations for:
 * <ul>
 *   <li>Unrestricted instantiation
 *   <li>Whitelisting by the plugin that provides the type
 *   <li>Blacklisting by the plugin that provides the type
 * </ul>
 */
package com.google.jenkins.plugins.dsl.restrict;