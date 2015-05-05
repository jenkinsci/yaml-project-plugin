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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This provides an annotation that can be used on {@code Describable} classes
 * to denote a shorthand that can be used when instantiating them from YAML.
 * For example, if my class were declared:
 * <pre>
 *   {@literal @}YamlTag(tag = "!foo")
 *   class Foo extends Describable ...
 * </pre>
 * Then in a YAML DSL users can write:
 * <pre>
 *   $class: !foo
 * </pre>
 * In place of:
 * <pre>
 *   $class: com.google.jenkins.plugins.example.Foo
 * </pre>
 *
 * @see DescribableYamlTransformProvider
 * @see YamlTransformProvider
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface YamlTag {
  /** The tag to register */
  String tag();

  /** The argument to the given tag */
  String arg() default "";
}
