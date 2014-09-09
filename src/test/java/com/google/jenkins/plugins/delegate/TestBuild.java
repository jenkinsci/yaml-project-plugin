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
package com.google.jenkins.plugins.delegate;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

/** Test */
public class TestBuild
    extends AbstractBuild<TestBranchAwareProject, TestBuild> {
  public TestBuild(TestBranchAwareProject project)
      throws IOException {
    super(project);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    execute(new TestExecution());
  }

  /** Test */
  protected class TestExecution extends AbstractBuild.AbstractRunner {
    /** {@inheritDoc} */
    @Override
    protected Result doRun(BuildListener listener)
        throws IOException, InterruptedException {

      // Attach an SCMRevisionAction with our revision
      {
        final SCMHead head = getParent().getBranch().getHead();
        final SCMSource source = getParent().getSource();
        final SCMRevision revision = source.fetch(head, listener);
        TestBuild.this.addAction(new SCMRevisionAction(checkNotNull(revision)));
      }

      try {
        project.innerItem.scheduleBuild2(0,
            new Cause.UpstreamCause(TestBuild.this)).get();
      } catch (ExecutionException e) {
        return Result.FAILURE;
      }
      return Result.SUCCESS;
    }

    /** {@inheritDoc} */
    @Override
    public void post2(BuildListener listener) {
    }

    /** {@inheritDoc} */
    @Override
    public void cleanUp(BuildListener listener) {
    }
  }
}
