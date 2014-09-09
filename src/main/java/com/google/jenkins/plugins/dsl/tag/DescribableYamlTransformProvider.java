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
package com.google.jenkins.plugins.dsl.tag;

import static java.util.logging.Level.WARNING;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.jenkins.plugins.dsl.util.YamlTransform;

import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;

import jenkins.model.Jenkins;

/**
 * This walks the Describable extensions registered with Jenkins, looking for
 * {@link YamlTag} annotations on them.
 */
@Extension(ordinal = 1000 /* explicit tagging trumps */)
public class DescribableYamlTransformProvider
    extends YamlTransformProvider {
  private static final Logger logger =
      Logger.getLogger(DescribableYamlTransformProvider.class.getName());

  /** {@inheritDoc} */
  @Override
  protected List<YamlTransform> provide() {
    ExtensionList<Descriptor> extensions =
        checkNotNull(Jenkins.getInstance()).getExtensionList(Descriptor.class);

    Map<String, ArgumentYamlTransform> results = Maps.newHashMap();
    for (ExtensionComponent<Descriptor> component :
             extensions.getComponents()) {
      Descriptor descriptor = component.getInstance();

      // Store a map from tag to ArgumentYamlTransform and "merge" xforms with
      // the same tag.  Otherwise, we won't do the right thing when the same tag
      // appears on N classes with distinct arguments (we will just choose a
      // winner instead of dispatching based on argument).
      List<BasicYamlTransform> xforms = of(descriptor.clazz);
      for (BasicYamlTransform xform : xforms) {
        if (!results.containsKey(xform.getTag())) {
          results.put(xform.getTag(), xform);
          continue;
        }
        // When we merge, we may not be a bsic transform anymore.
        final ArgumentYamlTransform merged =
            results.get(xform.getTag()).merge(xform);
        results.put(merged.getTag(), merged);
      }
    }

    // NOTE: there is no way to express a preference for one tag to be shown in
    // the YamlAction over another.  This is because the resulting merged
    // transforms are unordered.  A logical ordering might be to choose the
    // first entry from @YamlTags when several are provided, but if we have:
    //
    // @YamlTags({@YamlTag(tag="!foo", arg="a"), @YamlTag(tag="!bar", arg="a")})
    // class A ...           //  ^--- foo first, bar second
    //
    // @YamlTags({@YamlTag(tag="!bar", arg="b"), @YamlTag(tag="!foo", arg="b")})
    // class B ...           //  ^--- bar first, foo second
    //
    // We would have two yaml merged transforms, which depending on the class
    // would be ordered differently.
    //
    // NOTE: We can show a preference for those coming from this provider, but
    // not amongst those that come through for a particular class.
    return ImmutableList.<YamlTransform>copyOf(results.values());
  }

  private static List<BasicYamlTransform> of(
      final Class<? extends Describable> type) {
    List<BasicYamlTransform> xforms = Lists.newArrayList();
    final YamlTag yamlTag = (YamlTag) type.getAnnotation(YamlTag.class);
    if (yamlTag != null) {
      BasicYamlTransform xform = from(yamlTag, type);
      if (xform != null) {
        xforms.add(xform);
      }
    }

    final YamlTags yamlTags = (YamlTags) type.getAnnotation(YamlTags.class);
    if (yamlTags != null) {
      for (YamlTag tag : yamlTags.value()) {
        BasicYamlTransform xform = from(tag, type);
        if (xform != null) {
          xforms.add(xform);
        }
      }
    }

    return xforms;
  }

  private static BasicYamlTransform from(YamlTag yamlTag, Class type) {
    if (!yamlTag.tag().startsWith("!")) {
      logger.log(WARNING, Messages.DescribableYamlTransformProvider_BadTag(
          yamlTag.tag(), type.getName()));
      return null;
    }

    // Add an instance of the annotated requirement
    return new BasicYamlTransform(yamlTag.tag(), yamlTag.arg(), type);
  }
}