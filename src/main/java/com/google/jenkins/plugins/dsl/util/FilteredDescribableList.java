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
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.dsl.restrict.BadTypeException;
import com.google.jenkins.plugins.dsl.restrict.RestrictedTypeException;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Saveable;
import hudson.util.DescribableList;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * This is threaded into all {@link DescribableList} fields of objects so
 * that when they are asked to {@link #rebuild} or {@link #rebuildHetero}
 * they are given an opportunity to filter the available descriptors for
 * binding.
 *
 * @param <T> The describable type held within this list.
 * @param <D> The descriptor of the describable within this list.
 */
public class FilteredDescribableList
    <T extends Describable<T>, D extends Descriptor<T>>
    extends DescribableList<T, D> {
  /**
   * Called when we deserialize, which is after we've bound the object, so
   * filtration is no longer necessary.
   */
  public FilteredDescribableList() {
    this.filter = Predicates.alwaysTrue();
  }

  public FilteredDescribableList(
      Predicate<Descriptor<T>> filter, Saveable owner) {
    super(owner);
    this.filter = checkNotNull(filter);
  }
  private final Predicate<Descriptor<T>> filter;

  /** {@inheritDoc} */
  @Override
  public void rebuild(StaplerRequest req, JSONObject json,
      List<? extends Descriptor<T>> descriptors)
      throws FormException, IOException {
    // TODO(mattmoor): Add diagnostics if formData contains
    // uninstantiable classes.
    // NOTE: The way this works, I'm not sure this is possible,
    // since the potential class names are keys?
    // encoding is just replace('.', '-')
    super.rebuild(req, json, applyFilter(descriptors));
  }

  /** {@inheritDoc} */
  @Override
  public void rebuildHetero(StaplerRequest req, JSONObject formData,
      Collection<? extends Descriptor<T>> descriptors, String key)
      throws FormException, IOException {
    // Detect references to classes that aren't installed.
    final Object array = formData.get(key);
    if (array != null) {
      for (Object o : JSONArray.fromObject(array)) {
        validateClass(((JSONObject) o).getString("$class"), descriptors);
      }
    }
    super.rebuildHetero(req, formData, applyFilter(descriptors), key);
  }

  /**
   * Validates that the given $class is present on the Jenkins master,
   * and appropriately typed for the expected $class of describable.
   * @throws BadTypeException is there are any issues
   */
  private void validateClass(String className,
      Collection<? extends Descriptor<T>> descriptors) {
    final ClassLoader baseLoader =
        checkNotNull(Jenkins.getInstance()).getPluginManager().uberClassLoader;
    try {
      final Class type = baseLoader.loadClass(className);
      final Descriptor<T> descriptor =
          checkNotNull(Jenkins.getInstance()).getDescriptor(type);

      if (!descriptors.contains(descriptor)) {
        throw new BadTypeException(
            Messages.FilteredDescribableList_NotInList(className));
      }
    } catch (ClassNotFoundException e) {
      // TODO(mattmoor): and if its an unknown tag?  then what happens?
      throw new BadTypeException(
          Messages.FilteredDescribableList_Unavailable(className));
    }
  }

  /**
   * Replace matching descriptors with poison pills that throw when an
   * attempt is made to instantiate them.  This is so we don't silently
   * accept attempts to subvert our restrictions.
   */
  private <E extends Descriptor<T>> List<E> applyFilter(
      Collection<E> descriptors) {
    List<E> newList = Lists.newArrayList();
    for (E d : descriptors) {
      if (filter.apply(d)) {
        newList.add(d);
      } else {
        newList.add((E) new PoisonPillDescriptor<T>(d.clazz));
      }
    }
    return newList;
  }

  /**
   * A {@link Descriptor} we substitute for real descriptors that are
   * restricted, so that if an attempt is made to instantiate them an
   * exception is thrown.
   */
  public static class PoisonPillDescriptor<T extends Describable<T>>
      extends Descriptor<T> {
    public PoisonPillDescriptor(Class<? extends T> clazz) {
      super(clazz);
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public T newInstance(StaplerRequest req, JSONObject formData) {
      throw new RestrictedTypeException(
          Messages.FilteredDescribableList_Filtered(clazz.getName()));
    }
  }

  /**
   * Walk the fields of an {@code object} via reflection and populate fields
   * of type {@link DescribableList} with a {@link FilteredDescribableList}
   * with the provided {@link Predicate}.
   */
  public static void rewrite(Object object, Predicate predicate) {
    // TODO(mattmoor): This should be recursive.
    for (Class<?> clazz = object.getClass(); clazz != null;
         clazz = clazz.getSuperclass()) {
      for (Field field : clazz.getDeclaredFields()) {
        if (DescribableList.class.isAssignableFrom(field.getType())) {
          try {
            field.setAccessible(true);
            DescribableList list = (DescribableList) field.get(object);
            checkState(list == null || list.size() == 0);

            field.set(object, new FilteredDescribableList(
                predicate, (Saveable) object));
          } catch (IllegalAccessException e) {
            // Impossible, we have given ourselves access.
          }
        }
      }
    }
  }
}
