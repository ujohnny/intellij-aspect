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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Build-action entry point: merges databases and resolves local execution paths. */
public final class CompileCommandsMerger {
  private static final Gson JSON = new GsonBuilder()
      .setStrictness(Strictness.STRICT).disableHtmlEscaping().create();
  private static final String EXECROOT = "{EXECROOT}";
  private static final String DEVELOPER_DIR = "__BAZEL_XCODE_DEVELOPER_DIR__";
  private static final String SDKROOT = "__BAZEL_XCODE_SDKROOT__";
  private static final String APPLE_METADATA = "_apple_support";

  private CompileCommandsMerger() {}

  public static void main(String[] args) {
    try {
      // Bazel's multiline parameter file: one literal argument per line, no shell quoting.
      List<String> arguments = args.length == 1 && args[0].startsWith("@")
          ? Files.readAllLines(Path.of(args[0].substring(1))) : List.of(args);
      if (arguments.isEmpty()) {
        throw new IllegalArgumentException(
            "Usage: merge <output> [compile_commands_<target-name>.json | *.apple_support.txt ...]");
      }
      merge(Path.of(arguments.getFirst()),
          arguments.subList(1, arguments.size()).stream().map(Path::of).toList(),
          Path.of("").toRealPath());
    } catch (IOException | IllegalArgumentException | JsonParseException error) {
      System.err.println("ccdb_new: " + error.getMessage());
      System.exit(1);
    }
  }

  static void merge(Path output, List<Path> inputs, Path execroot) throws IOException {
    if (!execroot.isAbsolute()) {
      throw new IllegalArgumentException("Expected an absolute execroot: " + execroot);
    }
    Map<Path, List<String>> applePaths = new HashMap<>();
    TreeSet<Path> databases = new TreeSet<>();
    for (Path input : inputs) {
      Path path = input.normalize();
      String name = path.getFileName().toString();
      if (name.endsWith(".apple_support.txt")) {
        List<String> paths = Files.readAllLines(path);
        if (paths.size() != 2 || paths.stream().anyMatch(p -> !Path.of(p).isAbsolute())) {
          throw new IllegalArgumentException("Expected two absolute Apple paths in " + path);
        }
        applePaths.put(path, paths);
      } else if (name.startsWith("compile_commands_") && name.endsWith(".json")) {
        databases.add(path);
      } else {
        throw new IllegalArgumentException("Unexpected compile commands input: " + path);
      }
    }
    JsonArray merged = new JsonArray();
    for (Path database : databases) {
      JsonElement root;
      try (var reader = Files.newBufferedReader(database)) {
        root = JSON.fromJson(reader, JsonElement.class);
      }
      if (root == null || !root.isJsonArray()) {
        throw new IllegalArgumentException("Expected a JSON array in " + database);
      }
      for (JsonElement entry : root.getAsJsonArray()) {
        if (!entry.isJsonObject()) {
          throw new IllegalArgumentException("Expected a compile command object in " + database);
        }
        JsonObject command = entry.getAsJsonObject();
        List<String> paths = null;
        JsonElement apple = command.remove(APPLE_METADATA);
        if (apple != null) {
          paths = applePaths.get(Path.of(string(apple, database)).normalize());
          if (paths == null) {
            throw new IllegalArgumentException("Missing Apple paths " + apple + " for " + database);
          }
        }
        merged.add(expand(command, execroot.toString(), paths, database));
      }
    }
    // Parse and expand everything before touching the output.
    try (var writer = Files.newBufferedWriter(output)) {
      JSON.toJson(merged, writer);
      writer.write('\n');
    }
  }

  private static JsonElement expand(
      JsonElement value, String execroot, List<String> applePaths, Path database) {
    if (value.isJsonObject()) {
      JsonObject result = new JsonObject();
      for (var field : value.getAsJsonObject().entrySet()) {
        result.add(field.getKey(), expand(field.getValue(), execroot, applePaths, database));
      }
      return result;
    }
    if (value.isJsonArray()) {
      JsonArray result = new JsonArray();
      for (JsonElement element : value.getAsJsonArray()) {
        result.add(expand(element, execroot, applePaths, database));
      }
      return result;
    }
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
      String text = value.getAsString();
      if (applePaths == null && (text.contains(DEVELOPER_DIR) || text.contains(SDKROOT))) {
        throw new IllegalArgumentException("Apple placeholder without toolchain paths in " + database);
      }
      text = text.replace(EXECROOT, execroot)
          .replace("__BAZEL_EXECUTION_ROOT__", execroot)
          .replace("__BAZEL_EXECUTION_ROOT_CANONICAL__", execroot);
      if (applePaths != null) {
        text = text.replace(DEVELOPER_DIR, applePaths.get(0)).replace(SDKROOT, applePaths.get(1));
      }
      return new JsonPrimitive(text);
    }
    return value;
  }

  private static String string(JsonElement value, Path database) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Expected an Apple paths filename in " + database);
    }
    return value.getAsString();
  }
}
