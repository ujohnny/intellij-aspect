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

import com.intellij.aspect.tools.RunfilesRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BazelRunTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void collectsTransitiveDatabasesAndGeneratesBesideThem() throws Exception {
    String[] paths = System.getProperty("ccdb.databases").split(" ");
    assertEquals(3, paths.length);
    Map<Path, Path> databases = new TreeMap<>();
    for (String path : paths) {
      assertTrue(path.endsWith("/compile_commands.json"));
      Path copy = temporary.getRoot().toPath().resolve(path);
      Files.createDirectories(copy.getParent());
      Files.copy(RunfilesRepo.location(path), copy);
      databases.put(copy, Path.of(path).getParent().getParent());
    }
    List<Path> outputs = BazelConfigGenerator.generate(databases);
    assertEquals(2, outputs.size());
    List<String> configs = new ArrayList<>();
    for (Path output : outputs) {
      configs.add(Files.readString(output));
    }
    String direct = configs.stream().filter(text -> text.contains("alpha")).findFirst().orElseThrow();
    assertTrue(direct.contains("alpha\\\\.cc"));
    assertTrue(direct.contains("beta\\\\.cc"));
    assertEquals(2, direct.lines().filter(line -> line.startsWith("  CompilationDatabase:")).count());
    assertTrue(direct.contains("CompilationDatabase: \"alpha\""));
    assertTrue(direct.contains("CompilationDatabase: \"beta\""));
    String transitive = configs.stream().filter(text -> text.contains("library")).findFirst().orElseThrow();
    assertTrue(transitive.contains("library\\\\.cc"));
    assertFalse(direct.contains("sandbox"));
  }
}
