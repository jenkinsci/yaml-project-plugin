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
 * This package exposes Jenkins natural YAML-based DSL.
 * <p>
 * <b>Background:</b>
 * To understand why this is the natural DSL for Jenkins, first you should
 * be familiar with the way Jenkins performs structured form submission
 * using JSON blobs, whose subsections are recursively bound by various
 * plugins:
 * https://wiki.jenkins-ci.org/display/JENKINS/Structured+Form+Submission
 * <p>
 * The rest is simply leveraging the fact that "JSON syntax is a subset of
 * YAML version 1.2" -- http://en.wikipedia.org/wiki/YAML
 * <p>
 * <b>New job types</b>
 * This package exposes two new job types, one is a traditional job that takes
 * an SCM, and after syncing loads a specified DSL file and executes it.  The
 * other job type is a MultiBranchProject, leveraging the work in branch-api
 * to scan a given SCMSource for branches with a given DSL file, and
 * automatically instantiating jobs for each of them.
 * <p>
 * <b>Sugar</b>
 * To make it easier for people to see their DSL, we attach an approximation of
 * the reduced DSL to any job created through the Jenkins Web UI.  This turns
 * the Jenkins Web UI into an editor of sorts.
 * <p>
 * We also provide special syntactic sugar to avoid requiring users to have
 * plugin-developer level knowledge of Jenkins to use it (in particular knowing
 * fully qualified class names).  For most plugins, you can simply take the
 * name of the plugin's section of the UI, and simply write instead:
 * {@code !by-name Invoke top-level Maven targets}
 * Additionally, some plugins may expose even terser short-hand,
 * e.g. {@code !yaml}.
 * <p>
 * The suggested DSL for jobs created through the UI present this special
 * syntactic sugar to the user, whenever available.
 */
package com.google.jenkins.plugins.dsl;