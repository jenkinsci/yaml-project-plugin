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
 * This package exposes a set of extension points for providing the
 * end users of the DSL with syntactic sugar they can use in place of
 * raw class names when specifying plugins.
 * <p>
 * For providing additional YAML transformations, see
 * {@link YamlTransformProvider}.
 * <p>
 * By default, we expose three provider implementations:
 * <ol>
 *   <li> Built-ins, e.g. {@code !freestyle, !matrix, ...}
 *   <li> Explicitly tagged describables: {@literal @}{@code YamlTag}
 *   <li> A special display-name matched: {@code !by-name "Hello Builder"}
 * </ol>
 */
package com.google.jenkins.plugins.dsl.tag;