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
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class BazelRuleTest {
  @Test public void buildsOnlyConfigsAndIncludesTransitivePackages() throws Exception {
    String[] paths = System.getProperty("ccdb.configs").split(" ");
    assertEquals(2, paths.length);
    List<String> configs = new ArrayList<>();
    for (String path : paths) {
      assertTrue(path.endsWith("/.clangd"));
      configs.add(Files.readString(RunfilesRepo.location(path)));
    }
    String direct = configs.stream().filter(text -> text.contains("alpha")).findFirst().orElseThrow();
    assertTrue(direct.contains("alpha\\\\.cc"));
    assertTrue(direct.contains("beta\\\\.cc"));
    assertEquals(2, direct.lines().filter(line -> line.startsWith("  CompilationDatabase:")).count());
    assertTrue(direct.contains("/tools/ccdb/testdata/alpha\""));
    assertTrue(direct.contains("/tools/ccdb/testdata/beta\""));
    String transitive = configs.stream().filter(text -> text.contains("library")).findFirst().orElseThrow();
    assertTrue(transitive.contains("library\\\\.cc"));
    assertFalse(direct.contains("sandbox"));
  }
}
