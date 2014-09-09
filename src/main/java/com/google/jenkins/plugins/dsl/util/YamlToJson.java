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
import java.util.List;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import net.sf.json.JSONSerializer;

/**
 * This class encapsulates the translation of a Yaml object into
 * JSON, so Jenkins can data-bind it "as if" it were just submitted
 * through its form configuration.
 *
 * NOTE: This is not round-trippable with {@link JsonToYaml} because
 * the objects are stringified, flattening a possible DAG as a tree.
 * see: $/dsl/src/test/resources/com/google/jenkins/plugins/dsl/util/hoist.yaml
 * for an example of what the Yaml DSL allows, which the stringify
 * would flatten.
 */
public abstract class YamlToJson {
  /**
   * This method is the workhorse method that implementations provide for
   * translating yaml into json.
   */
  public abstract String toJson(InputStream inputStream);

  /** @see #toJson(InputStream) */
  public final String toJson(String input) {
    return toJson(new ByteArrayInputStream(input.getBytes(UTF_8)));
  }

  /** Stringify the object that results from loading the given Yaml */
  public static class Default extends YamlToJson {
    public Default() {
      this(ImmutableList.<YamlTransform>of());
    }

    public Default(List<YamlTransform> transforms) {
      this.transforms = checkNotNull(transforms);
    }

    /** {@inheritDoc} */
    @Override
    public String toJson(InputStream inputStream) {
      // From Yaml to Object
      Yaml yaml = new Yaml(new CustomConstructor(transforms));
      // From Object to JSON (as string)
      return JSONSerializer.toJSON(yaml.load(inputStream)).toString();
    }

    private final List<YamlTransform> transforms;
  }

  private static class CustomConstructor extends SafeConstructor {
    public CustomConstructor(List<YamlTransform> transforms) {
      for (YamlTransform xform : transforms) {
        final String tag = xform.getTag();
        this.yamlConstructors.put(new Tag(tag), new ConstructFoo(xform));
      }
    }

    private class ConstructFoo extends AbstractConstruct {
      public ConstructFoo(YamlTransform transform) {
        this.transform = checkNotNull(transform);
      }

      public Object construct(Node node) {
        final String val = (String) constructScalar((ScalarNode) node);
        return transform.construct(val);
      }

      private final YamlTransform transform;
    }
  }
}