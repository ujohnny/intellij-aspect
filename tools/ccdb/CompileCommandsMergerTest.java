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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompileCommandsMergerTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private Path file(String name, String contents) throws Exception {
    Path path = temporary.getRoot().toPath().resolve(name);
    Files.createDirectories(path.getParent());
    return Files.writeString(path, contents);
  }

  private Path output() {
    return temporary.getRoot().toPath().resolve("merged.json");
  }

  private Path execroot() {
    return temporary.getRoot().toPath().resolve("exec root \"quoted\" \\ $&");
  }

  private Path database(String target, String source, String flag) throws Exception {
    JsonObject entry = new JsonObject();
    entry.addProperty("directory", "{EXECROOT}");
    entry.addProperty("file", source);
    JsonArray arguments = new JsonArray();
    arguments.add("clang++");
    arguments.add(flag);
    arguments.add("-c");
    arguments.add(source);
    entry.add("arguments", arguments);
    JsonArray entries = new JsonArray();
    entries.add(entry);
    return file("compile_commands_" + target + ".json", new Gson().toJson(entries));
  }

  private JsonArray result() throws Exception {
    return JsonParser.parseString(Files.readString(output())).getAsJsonArray();
  }

  @Test public void mergesDeterministicallyAndDeduplicatesInputFiles() throws Exception {
    Path first = database("a", "same.cc", "-DONE");
    Path second = database("b", "same.cc", "-DTWO");
    CompileCommandsMerger.merge(output(), List.of(second, first, first), Map.of(), execroot());
    JsonArray entries = result();
    assertEquals(2, entries.size()); // Preserve different commands for the same source.
    assertEquals("-DONE", entries.get(0).getAsJsonObject().getAsJsonArray("arguments").get(1).getAsString());
    assertEquals(execroot().toString(), entries.get(0).getAsJsonObject().get("directory").getAsString());
    String original = Files.readString(output());
    CompileCommandsMerger.merge(output(), List.of(first, second), Map.of(), execroot());
    assertEquals(original, Files.readString(output()));
  }

  @Test public void expandsEachToolchainsApplePathsWithoutBreakingJson() throws Exception {
    String developer = execroot().resolve("Xcode.app/Contents/Developer").toString();
    Path mac = file("mac.apple_support.txt", developer + "\n/SDKs/Mac OS.sdk\n");
    Path ios = file("ios.apple_support.txt", "/Xcode-iOS\n/SDKs/iPhone.sdk\n");
    Path first = database("a", "a.cc",
        "__BAZEL_XCODE_DEVELOPER_DIR__/usr/bin:__BAZEL_XCODE_SDKROOT__");
    Path second = database("b", "b.cc", "-isysroot=__BAZEL_XCODE_SDKROOT__");
    CompileCommandsMerger.merge(output(), List.of(first, second, mac, ios), Map.of(first, mac, second, ios), execroot());
    JsonArray entries = result();
    assertEquals(developer + "/usr/bin:/SDKs/Mac OS.sdk",
        entries.get(0).getAsJsonObject().getAsJsonArray("arguments").get(1).getAsString());
    assertEquals("-isysroot=/SDKs/iPhone.sdk",
        entries.get(1).getAsJsonObject().getAsJsonArray("arguments").get(1).getAsString());
    assertFalse(Files.readString(output()).contains("_apple_support"));
  }

  @Test public void expandsCommandFileOutputAndAppleExecrootPlaceholders() throws Exception {
    JsonObject entry = new JsonObject();
    entry.addProperty("directory", "{EXECROOT}");
    entry.addProperty("file", "{EXECROOT}/a.cc");
    entry.addProperty("output", "{EXECROOT}/a.o");
    entry.addProperty("command", "clang -fdebug-prefix-map=__BAZEL_EXECUTION_ROOT__=. "
        + "-I__BAZEL_EXECUTION_ROOT_CANONICAL__/include");
    Path database = file("compile_commands_a.json", "[" + entry + "]");
    CompileCommandsMerger.merge(output(), List.of(database), Map.of(), execroot());
    JsonObject command = result().get(0).getAsJsonObject();
    assertEquals(execroot() + "/a.cc", command.get("file").getAsString());
    assertEquals(execroot() + "/a.o", command.get("output").getAsString());
    assertEquals("clang -fdebug-prefix-map=" + execroot() + "=. -I" + execroot() + "/include",
        command.get("command").getAsString());
  }

  @Test public void acceptsEmptyAndNonAppleGraphs() throws Exception {
    CompileCommandsMerger.merge(output(), List.of(), Map.of(), execroot());
    assertTrue(result().isEmpty());
    Path empty = file("compile_commands_empty.json", "[]");
    CompileCommandsMerger.merge(output(), List.of(empty), Map.of(), execroot());
    assertTrue(result().isEmpty());
  }

  @Test public void rejectsMissingAppleDataBeforeOverwritingOutput() throws Exception {
    Path apple = file("mac.apple_support.txt", "/Xcode\n/SDK\n");
    Path database = database("a", "a.cc", "__BAZEL_XCODE_SDKROOT__");
    Files.writeString(output(), "previous output");
    var error = assertThrows(IllegalArgumentException.class,
        () -> CompileCommandsMerger.merge(output(), List.of(database), Map.of(database, apple), execroot()));
    assertTrue(error.getMessage().contains("Missing Apple paths"));
    assertEquals("previous output", Files.readString(output()));
    Path unbound = database("b", "b.cc", "__BAZEL_XCODE_SDKROOT__");
    assertThrows(IllegalArgumentException.class,
        () -> CompileCommandsMerger.merge(output(), List.of(unbound, apple), Map.of(), execroot()));
  }

  @Test public void rejectsMalformedInputs() throws Exception {
    for (String content : List.of("{}", "[1]", "null")) {
      Path malformed = file("compile_commands_bad.json", content);
      assertThrows(IllegalArgumentException.class,
          () -> CompileCommandsMerger.merge(output(), List.of(malformed), Map.of(), execroot()));
    }
    Path badApple = file("bad.apple_support.txt", "/Xcode\nrelative-sdk\n");
    assertThrows(IllegalArgumentException.class,
        () -> CompileCommandsMerger.merge(output(), List.of(badApple), Map.of(), execroot()));
  }

  @Test public void readsMultilineParameterFilesWithLiteralSpaces() throws Exception {
    Path database = database("target with spaces", "a.cc", "-isysroot=__BAZEL_XCODE_SDKROOT__");
    Path apple = file("mac paths.apple_support.txt", "/Xcode\n/SDKs/Mac OS.sdk\n");
    Path manifest = file("apple support.json",
        new Gson().toJson(Map.of(database.toString(), apple.toString())));
    Path params = file("arguments.params",
        output() + "\n" + manifest + "\n" + database + "\n" + apple + "\n");
    CompileCommandsMerger.main(new String[] {"@" + params});
    assertEquals(1, result().size());
    assertEquals(Path.of("").toRealPath().toString(),
        result().get(0).getAsJsonObject().get("directory").getAsString());
    assertEquals("-isysroot=/SDKs/Mac OS.sdk",
        result().get(0).getAsJsonObject().getAsJsonArray("arguments").get(1).getAsString());
  }
}
