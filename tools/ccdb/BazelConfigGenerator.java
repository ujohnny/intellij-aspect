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

import com.google.gson.JsonArray;
import com.intellij.aspect.tools.RunfilesRepo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Generates configs beside Bazel databases when explicitly invoked by bazel run. */
final class BazelConfigGenerator {
  private BazelConfigGenerator() {}

  static List<Path> generate(List<String> runfiles) throws IOException {
    Map<Path, Path> databases = new TreeMap<>();
    for (String runfile : runfiles) {
      // Resolve only database runfile links: writes belong beside the actual build outputs.
      databases.put(RunfilesRepo.location(runfile).toRealPath(), sourcePrefix(runfile));
    }
    return generate(databases);
  }

  static Path sourcePrefix(String runfile) {
    Path target = Path.of(runfile).getParent();
    if (target == null) {
      throw new IllegalArgumentException("Database needs a target parent directory: " + runfile);
    }
    Path prefix = target.getParent();
    if (prefix == null) {
      return Path.of(""); // Target in the main repository's root package.
    }
    if (prefix.startsWith("..")) {
      return Path.of("external").resolve(prefix.subpath(1, prefix.getNameCount()));
    }
    return prefix;
  }

  static List<Path> generate(Map<Path, Path> databases) throws IOException {
    Map<Path, Map<Path, Set<String>>> folders = new TreeMap<>();
    Map<Path, Map<Path, Path>> owners = new TreeMap<>();
    for (var entry : databases.entrySet()) {
      Path database = entry.getKey();
      if (!database.getFileName().toString().equals("compile_commands.json")) {
        throw new IllegalArgumentException("Expected compile_commands.json: " + database);
      }
      Path folder = database.getParent().getParent();
      Map<Path, Set<String>> targets = folders.computeIfAbsent(folder, unused -> new TreeMap<>());
      JsonArray commands = ClangdGenerator.readDatabase(database);
      if (commands.isEmpty()) {
        continue;
      }
      if (!commands.get(0).isJsonObject()) {
        throw new IllegalArgumentException("Expected a compile command object in " + database);
      }
      Path execroot = Path.of(ClangdGenerator.stringField(
          commands.get(0).getAsJsonObject(), "directory", database.toString()));
      if (!execroot.isAbsolute()) {
        throw new IllegalArgumentException("Expected an absolute execroot directory in " + database);
      }
      Path sourceFolder = execroot.resolve(entry.getValue()).normalize();
      Set<String> sources = ClangdGenerator.readSources(commands, database, sourceFolder,
          owners.computeIfAbsent(folder, unused -> new TreeMap<>()));
      targets.put(database.getParent(), sources);
    }
    Map<Path, String> outputs = new TreeMap<>();
    for (var folder : folders.entrySet()) {
      outputs.put(folder.getKey().resolve(".clangd"),
          ClangdGenerator.render(folder.getKey(), folder.getValue()));
    }
    // Validate all inputs before overwriting any config. No Bazel action declares these files.
    for (var output : outputs.entrySet()) {
      Files.writeString(output.getKey(), output.getValue());
    }
    return List.copyOf(outputs.keySet());
  }
}
