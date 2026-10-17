/*
 * Copyright 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.aspect.tools.ccdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.intellij.aspect.tools.RunfilesRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class CompileCommandsRuleTest {
  @Test public void intermediateDatabasesContainOnlyCompileCommands() throws Exception {
    String[] paths = System.getProperty("ccdb.databases").split(" ");
    assertEquals(3, paths.length);
    for (String path : paths) {
      assertTrue(Path.of(path).getFileName().toString().startsWith("compile_commands_"));
      String text = Files.readString(RunfilesRepo.location(path));
      for (var entry : JsonParser.parseString(text).getAsJsonArray()) {
        assertEquals(Set.of("directory", "file", "arguments"), entry.getAsJsonObject().keySet());
      }
    }
  }

  @Test public void mergesDirectAndTransitiveCommandsWithResolvedPaths() throws Exception {
    String text = Files.readString(RunfilesRepo.location(System.getProperty("ccdb.merged")));
    JsonArray commands = JsonParser.parseString(text).getAsJsonArray();
    assertEquals(3, commands.size());
    Set<String> sources = new HashSet<>();
    for (var entry : commands) {
      var command = entry.getAsJsonObject();
      sources.add(command.get("file").getAsString());
      String directory = command.get("directory").getAsString();
      assertTrue(Path.of(directory).isAbsolute());
      assertFalse(directory.contains("sandbox"));
      var arguments = command.getAsJsonArray("arguments");
      assertTrue(arguments.size() > 3);
      if (command.get("file").getAsString().endsWith("alpha.cc")) {
        assertTrue(arguments.toString().contains("-DTARGET_ALPHA"));
      }
    }
    assertEquals(Set.of("tools/ccdb/testdata/alpha.cc", "tools/ccdb/testdata/beta.cc",
        "tools/ccdb/testdata/sub/library.cc"), sources);
    assertFalse(text.contains("{EXECROOT}"));
    assertFalse(text.contains("__BAZEL_XCODE_"));
    assertFalse(text.contains("__BAZEL_EXECUTION_ROOT"));
    assertFalse(text.contains("_apple_support"));
  }

  @Test public void headersOnlyGraphProducesEmptyDatabase() throws Exception {
    String text = Files.readString(RunfilesRepo.location(System.getProperty("ccdb.empty")));
    assertTrue(JsonParser.parseString(text).getAsJsonArray().isEmpty());
  }
}
