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
 * This package implements a set of utilities utilized by the DSL for
 * translating between JSON and YAML, binding the resulting JSON to Jenkins
 * objects, applying transformations to the YAML, filtering the overly verbose
 * JSON submitted by Jenkins forms, and lastly a special DescribableList that
 * can patch itself into objects to limit what Describables can be added.
 */
package com.google.jenkins.plugins.dsl.util;