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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.emitter.ScalarAnalysis;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

/**
 * This class is basically a no-op, since Yaml 1.2 is a superset
 * of JSON; however, this scrubs some of the excess quoting and
 * scoping away, so that it looks more like Yaml and less like JSON.
 */
public abstract class JsonToYaml {
  /**
   * This method is the workhorse method that implementations provide for
   * translating json into yaml.
   */
  public abstract String toYaml(InputStream inputStream);

  /**
   * @see #toYaml(InputStream)
   */
  public final String toYaml(String input) {
    return toYaml(new ByteArrayInputStream(input.getBytes(UTF_8)));
  }

  /**
   * A basic implementation that parses and dumps the yaml using Snake Yaml.
   */
  public static class Default extends JsonToYaml {
    public Default() {
      this(ImmutableList.<YamlTransform>of());
    }

    public Default(List<YamlTransform> transforms) {
      this.transforms = checkNotNull(transforms);
    }

    /** {@inheritDoc} */
    @Override
    public String toYaml(InputStream inputStream) {
      DumperOptions options = new DumperOptions() {
          /** Force usage of PLAIN style */
          @Override
          public DumperOptions.ScalarStyle calculateScalarStyle(
              ScalarAnalysis analysis, DumperOptions.ScalarStyle style) {
            return DumperOptions.ScalarStyle.PLAIN;
          }
        };
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      // NOTE: This is inadequate, thus the above hack.
      options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
      final Yaml yaml = new Yaml(new CustomRepresenter(transforms), options);

      return yaml.dump(yaml.load(inputStream));
    }

    private final List<YamlTransform> transforms;
  }

  private static class CustomRepresenter extends Representer {
    public CustomRepresenter(List<YamlTransform> transforms) {
      this.representers.put(String.class, new RepresentTransforms(transforms));
    }

    private class RepresentTransforms implements Represent {
      public RepresentTransforms(final List<YamlTransform> inputTransforms) {
        final Map<String, YamlTransform> transforms = Maps.newHashMap();
        final Map<String, Class> classes = Maps.newHashMap();

        for (YamlTransform xform : inputTransforms) {
          for (Class clazz : xform.getClasses()) {
            final String text = xform.construct(xform.represent(clazz));
            // The first transform "wins"
            if (classes.containsKey(text)) {
              continue;
            }
            transforms.put(text, xform);
            classes.put(text, clazz);
          }
        }

        // Store to the final fields as an immutable version
        this.transforms = Collections.unmodifiableMap(transforms);
        this.classes = Collections.unmodifiableMap(classes);
      }

      public Node representData(Object data) {
        final String element = (String) data;
        if (transforms.containsKey(element)) {
          final YamlTransform xform = transforms.get(element);
          final Class clazz = classes.get(element);
          return representScalar(new Tag(xform.getTag()),
              xform.represent(clazz));
        }
        return representScalar(Tag.STR, element);
      }

      private final Map<String, YamlTransform> transforms;
      private final Map<String, Class> classes;
    }
  }
}