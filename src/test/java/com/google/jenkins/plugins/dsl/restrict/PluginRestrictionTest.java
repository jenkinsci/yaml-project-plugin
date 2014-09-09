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
package com.google.jenkins.plugins.dsl.restrict;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;

/** Tests for {@link PluginWhitelist} and {@link PluginBlackList}. */
public class PluginRestrictionTest {
  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Mock
  private PluginWrapper plugin;

  @Mock
  private PluginManager manager;

  private PluginWhitelist whitelist;
  private PluginBlacklist blacklist;

  private void setup(List<String> plugins) {
    // NOTE: It doesn't seem as though JenkinsRule
    // properly sets up the PluginManager
    whitelist = new PluginWhitelist(plugins) {
        @Override
        public PluginManager getPluginManager() {
          return manager;
        }
      };
    blacklist = new PluginBlacklist(plugins) {
        @Override
        public PluginManager getPluginManager() {
          return manager;
        }
      };
  }

  private RestrictedProject fakeProject;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    final AbstractProject project = jenkins.createFreeStyleProject("test");
    this.fakeProject =
        new RestrictedProject<AbstractProject>() {
      @Override
      public AbstractProject asProject() {
        return project;
      }

      @Override
      public AbstractRestriction getRestriction() {
        return null;
      }
    };
  }

  @Test
  public void testBuiltin() throws Exception {
    setup(ImmutableList.of("git-plugin"));

    assertEquals(FreeStyleProject.class,
        whitelist.getClassLoader(fakeProject).loadClass(
            FreeStyleProject.class.getName()));
    assertEquals(FreeStyleProject.class,
        blacklist.getClassLoader(fakeProject).loadClass(
            FreeStyleProject.class.getName()));
  }

  @Test
  public void testWhitelistSuccess() throws Exception {
    setup(ImmutableList.of("git-plugin"));
    when(manager.whichPlugin(GitSCM.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("git-plugin");

    // Everything is kosher, we should get our class
    assertEquals(GitSCM.class,
        whitelist.getClassLoader(fakeProject).loadClass(
            GitSCM.class.getName()));
  }

  @Test(expected = RestrictedTypeException.class)
  public void testWhitelistFailure() throws Exception {
    setup(ImmutableList.of("other-plugin"));
    when(manager.whichPlugin(GitSCM.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("git-plugin");

    // Not a whitelisted plugin
    whitelist.getClassLoader(fakeProject).loadClass(GitSCM.class.getName());
  }

  @Test
  public void testBlacklistSuccess() throws Exception {
    setup(ImmutableList.of("other-plugin"));
    when(manager.whichPlugin(GitSCM.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("git-plugin");

    // Everything is kosher, we should get our class
    assertEquals(GitSCM.class,
        blacklist.getClassLoader(fakeProject).loadClass(
            GitSCM.class.getName()));
  }

  @Test(expected = RestrictedTypeException.class)
  public void testBlacklistFailure() throws Exception {
    setup(ImmutableList.of("git-plugin"));
    when(manager.whichPlugin(GitSCM.class)).thenReturn(plugin);
    when(plugin.getShortName()).thenReturn("git-plugin");

    // A blacklisted plugin
    blacklist.getClassLoader(fakeProject).loadClass(GitSCM.class.getName());
  }

  @Test(expected = BadTypeException.class)
  public void trulyUnknownClass() throws Exception {
    setup(ImmutableList.of("git-plugin"));

    // A garbage type
    blacklist.getClassLoader(fakeProject).loadClass("com.google.MyUnknownType");
  }
}