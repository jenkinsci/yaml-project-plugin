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
package com.google.jenkins.plugins.dsl.util;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;

import org.directwebremoting.util.FakeHttpServletRequest;
import org.directwebremoting.util.FakeHttpServletResponse;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.ResponseImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.jenkins.plugins.dsl.restrict.BadTypeException;
import com.google.jenkins.plugins.dsl.restrict.RestrictedTypeException;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

/**
 * This interface abstracts how our various modules bind a {@link JSONObject} to
 * a particular type.
 */
public interface Binder {
  /**
   * Bind the {@link JSONObject} to the specified type.
   *
   * @param <T> The type to which we are binding the JSON.
   * @param json The serialized object we are instantiating
   * @return The object of the specified type, from the JSON.
   */
  <T extends Describable> T bind(JSONObject json)
      throws IOException, FormException;

  /**
   * Bind the {@link JSONObject} to the specified type of {@link Job}.
   *
   * @param <T> The type to which we are binding the JSON.
   * @param parent The item group within which we are creating the job
   * @param name The name to give the bound job.
   * @param json The serialized job we are instantiating
   * @return The object of the specified type, from the JSON.
   */
  <T extends Job> T bindJob(ItemGroup<? super T> parent,
      String name, JSONObject json) throws IOException;

  /**
   * Bind the {@link JSONObject} to the specified type of {@link View}.
   *
   * @param <T> The type to which we are binding the JSON.
   * @param parent The mixin for the view group where we are creating the view
   * @param name The name to give the bound view.
   * @param json The serialized view we are instantiating
   * @return The object of the specified type, from the JSON.
   */
  <T extends View> T bindView(ViewGroupMixIn parent,
      String name, JSONObject json) throws IOException, FormException;

  /**
   * The default implementation of {@link Binder}, which leverages
   * {@link Stapler} to perform the binding as it would when handing
   * the object as form input.
   */
  public class Default implements Binder {
    public Default(ClassLoader classLoader) {
      this.classLoader = checkNotNull(classLoader);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Describable> T bind(JSONObject json)
        throws IOException, FormException {
      final String clazz = checkNotNull(json.optString("$class", null));

      final Descriptor descriptor = getDescriptor(clazz);

      final Stapler stapler = getStapler();
      final StaplerRequest request = getRequest(stapler, json);

      // We do this instead of 'request.bindJson' because this doesn't
      // require a DataBoundConstructor.
      // TODO(mattmoor): Should we do the rewrite of describable lists
      // here as well?
      return (T) descriptor.newInstance(request, json);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Job> T bindJob(ItemGroup<? super T> parent,
        String name, JSONObject json) throws IOException {
      // TODO(mattmoor): What if: FreeStyleProject != T?
      final String clazz = json.optString("$class",
          FreeStyleProject.class.getName());

      final TopLevelItemDescriptor descriptor =
          (TopLevelItemDescriptor) getDescriptor(clazz);

      final T project = (T) descriptor.newInstance(parent, name);

      final Predicate<Descriptor> hasClazz = new Predicate<Descriptor>() {
        public boolean apply(Descriptor descriptor) {
          try {
            return null != getClassLoader().loadClass(
                descriptor.clazz.getName());
          } catch (RestrictedTypeException e) {
            return false;
          } catch (ClassNotFoundException e) {
            return false;
          }
        }
      };

      FilteredDescribableList.rewrite(project, hasClazz);

      final Stapler stapler = getStapler();
      final StaplerRequest request = getRequest(stapler, json);
      final StaplerResponse response = getResponse(stapler);

      try {
        project.doConfigSubmit(request, response);
      } catch (FormException e) {
        throw new IllegalStateException(Messages.DefaultBinder_BadJsonBlob(
            json.toString()), e);
      } catch (ServletException e) {
        throw new IllegalStateException(Messages.DefaultBinder_BadJsonBlob(
            json.toString()), e);
      }
      return project;
    }

    /** {@inheritDoc} */
    @Override
    public <T extends View> T bindView(ViewGroupMixIn parentMixIn,
        String name, JSONObject json) throws IOException, FormException {
      T view = bind(json);
      // We need to do this to set the owner, so doConfigSubmit works
      // properly with things like ListView.
      parentMixIn.addView(view);

      // TODO(mattmoor): Should we do the rewrite of describable lists
      // here as well?

      final Stapler stapler = getStapler();
      final StaplerRequest request = getRequest(stapler, json);
      final StaplerResponse response = getResponse(stapler);

      try {
        view.doConfigSubmit(request, response);
        view.save();
      } catch (FormException e) {
        throw new IllegalStateException(Messages.DefaultBinder_BadJsonBlob(
            json.toString()), e);
      } catch (ServletException e) {
        throw new IllegalStateException(Messages.DefaultBinder_BadJsonBlob(
            json.toString()), e);
      }
      return view;
    }

    /**
     * Gets the {@link Descriptor} of the {@link Describable} we are
     * looking to instantiate.
     */
    @VisibleForTesting
    Descriptor getDescriptor(String clazzName) throws IOException {
      Class<? extends Describable<?>> implClass;
      try {
        implClass = (Class<? extends Describable<?>>) getClassLoader()
            .loadClass(clazzName);
      } catch (ClassNotFoundException ex) {
        throw new BadTypeException(
            Messages.DefaultBinder_CannotLoadClass(clazzName));
      }

      final Descriptor descriptor =
          checkNotNull(Jenkins.getInstance()).getDescriptor(implClass);
      if (descriptor == null) {
        throw new BadTypeException(
            Messages.DefaultBinder_NoDescriptor(implClass.getName()));
      }
      return descriptor;
    }

    /** @return the class loader through which to load classes */
    public ClassLoader getClassLoader() {
      // This class loader is used in three ways:
      // 1) To determine the initial descriptor via which we allocate our
      //   root describable object.
      //
      // 2) It is embedded in the Stapler we create, so bound objects must
      //   have a type available from it.
      //
      // 3) It is used as a Predicate in the FilteredDescribableList's we
      //   install in every DescribableList field of instantiated objects.
      return classLoader;
    }
    private final ClassLoader classLoader;

    /** @return a response to use with {@link Stapler} binding utilities */
    @VisibleForTesting
    StaplerResponse getResponse(Stapler stapler) {
      return new ResponseImpl(stapler,
          new FakeHttpServletResponse()) {
        @Override
        public void sendRedirect(String url) throws IOException {
          ;
        }
      };
    }

    /**
     * A request to use with {@link Stapler} that pretends the given
     * json block is submitted form data, to allow it to be sent through
     * the object creation process.
     */
    @VisibleForTesting
    StaplerRequest getRequest(Stapler stapler, final JSONObject json) {
      return new RequestImpl(stapler,
          new FakeHttpServletRequest() {
            @Override
            public String getRequestURI() {
              return "/";
            }
          }, Collections.EMPTY_LIST, null) {

        @Override
        public JSONObject getSubmittedForm() {
          return json;
        }

        @Override
        public String getParameter(String name) {
          Object o = getSubmittedForm().opt(name);
          if (o instanceof JSONObject) {
            return ((JSONObject) o).optString("value");
          } else if (o != null) {
            return o.toString();
          }
          return null;
        }
      };
    }

    /** Gets an instance of stapler for use in lazy json binding. */
    @VisibleForTesting
    Stapler getStapler() {
      final WebApp webapp = new WebApp(null /* context */);
      webapp.setClassLoader(getClassLoader());

      Stapler stapler = new Stapler() {
          @Override
          public WebApp getWebApp() {
            return webapp;
          }
        };
      return stapler;
    }
  }
}
