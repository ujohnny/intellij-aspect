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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Expands UTF-8 @files, resolving their contents relative to the containing file. */
final class ResponseFiles {
  private ResponseFiles() {}

  static List<Path> expand(List<String> arguments, Path base) throws IOException {
    List<Path> paths = new ArrayList<>();
    expand(arguments, base, new HashSet<>(), paths);
    return paths;
  }

  private static void expand(
      List<String> arguments, Path base, Set<Path> active, List<Path> paths)
      throws IOException {
    for (String argument : arguments) {
      if (argument.isEmpty() || argument.equals("@")) {
        throw new IllegalArgumentException("Empty database or response-file path");
      }
      if (!argument.startsWith("@")) {
        paths.add(base.resolve(argument).toAbsolutePath().normalize());
        continue;
      }
      Path response = base.resolve(argument.substring(1)).toAbsolutePath().normalize();
      Path identity = response.toRealPath();
      if (!active.add(identity)) {
        throw new IllegalArgumentException("Response-file cycle: " + response);
      }
      if (active.size() > 64) {
        throw new IllegalArgumentException("Response-file nesting exceeds 64: " + response);
      }
      try {
        expand(tokenize(Files.readString(response), response), response.getParent(), active, paths);
      } finally {
        active.remove(identity);
      }
    }
  }

  // Shell-style whitespace, quotes and escapes; no variable or glob expansion.
  private static List<String> tokenize(String text, Path response) {
    List<String> tokens = new ArrayList<>();
    StringBuilder token = new StringBuilder();
    char quote = 0;
    boolean started = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (i == 0 && c == '\ufeff') {
        continue;
      }
      if (c == '\\' && quote != '\'') {
        if (++i == text.length()) {
          throw new IllegalArgumentException("Trailing escape in response file: " + response);
        }
        token.append(text.charAt(i));
        started = true;
      } else if (quote != 0) {
        if (c == quote) {
          quote = 0;
        } else {
          token.append(c);
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
        started = true;
      } else if (c == '#' && !started) {
        while (i < text.length() && text.charAt(i) != '\n') {
          i++;
        }
      } else if (Character.isWhitespace(c)) {
        if (started) {
          tokens.add(token.toString());
          token.setLength(0);
          started = false;
        }
      } else {
        token.append(c);
        started = true;
      }
    }
    if (quote != 0) {
      throw new IllegalArgumentException("Unclosed quote in response file: " + response);
    }
    if (started) {
      tokens.add(token.toString());
    }
    return tokens;
  }
}
