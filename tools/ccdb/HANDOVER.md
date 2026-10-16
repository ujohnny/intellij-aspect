# ccdb agent handover

Snapshot: 2026-10-16. Repository: `/Users/enovozhilov/triage/bazel-hackathon/intellij-aspect`.

## Current decisions

The user replaced build-action generation with an explicit, unsandboxed
`bazel run` executable. They explicitly chose to write `.clangd` **beside the
databases under `bazel-out`**, not into original source packages.

- `ccdb` is now a macro wrapping `java_binary`, not a config-generating rule.
- Its collection-only helper applies the configured IntelliJ aspect to `deps`,
  collects the transitive compilation databases, and exposes them as runfiles.
- `TemplateVariableInfo` passes sorted, shell-quoted database paths through the
  binary's `args`. This also supports empty graphs, whereas `$(rootpaths ...)`
  rejects a target with no files.
- The executable receives `--bazel-runfiles <database paths...>` and runs outside
  the build action sandbox. There is no manifest or config-generation action.
- A build alone prepares the executable and databases. Generated `.clangd` files
  are side effects of the explicit run, not declared Bazel outputs.
- Keep Java 21 and existing Gson/JUnit dependencies. No Kotlin or toolchain upgrade.

## Code map

| File | Responsibility |
| --- | --- |
| `ClangdGenerator.java` | CLI, standalone grouping, shared database parsing/source validation/config rendering |
| `ResponseFiles.java` | Nested response files, quoting, escapes, comments, cycle/depth checks |
| `BazelConfigGenerator.java` | Runfile resolution, original source-package mapping, generation beside databases |
| `ccdb.bzl` | Configured aspect, collection-only helper, executable macro |
| `BUILD` | Java library/binary/tests and Abseil example |
| `ClangdGeneratorTest.java` | Standalone and Bazel-mode unit tests |
| `BazelRunTest.java` | Integration test using actual transitive aspect databases copied into a temporary directory |
| `testdata/BUILD` | Two local C++ targets, a transitive subpackage, and an empty/header-only graph |
| `../../modules/compilecommands.bzl` | Database producer; emits `<target>/compile_commands.json` for clangd lookup |

Java package: `com.intellij.aspect.tools.ccdb`.

## Bazel paths and output

The executable resolves database runfile links with `toRealPath()` so configs
are written beside the actual build outputs. Database contents record the original
absolute execroot in command `directory`. Source prefixes come from database
runfile paths: `pkg/target/compile_commands.json` maps to `pkg`, while
`../repo/pkg/target/compile_commands.json` maps to `external/repo/pkg`.
Source paths themselves retain lexical/symlink identity.

Output example:

```text
bazel-out/<configuration>/bin/pkg/
  .clangd
  targetA/compile_commands.json
  targetB/compile_commands.json
```

External outputs stay under `bin/external/<repo>/`. `PathMatch` entries are
source-package-relative; `CompilationDatabase` references are relative target
directories. These configs need a matching source layout or clangd path mapping.
Building or running does not install configs in original source packages, copy
sources, or create mirrors. Copying a config elsewhere requires adjusting its
relative database references.

The aspect's existing local action expands `{EXECROOT}` using `$PWD`. Its template
remains `<target>.compile_commands.json.tpl`. This producer change is preserved.

## Standalone behavior

Direct database paths and `@response` files still work. For
`compdb/src/targetA/compile_commands.json`, output is `compdb/src/.clangd`, with
relative target database references and no redundant root config.

Direct arguments resolve against the invocation directory, honoring
`BUILD_WORKING_DIRECTORY`. Response-file paths resolve against their containing
file, support quoting/comments/nesting, and reject cycles or depth above 64.
Sources must fall under their configuration folder; paths are normalized without
resolving source symlinks. Inputs/commands are deduplicated. Ambiguous assignments
of one source to different databases in a group are rejected.

All inputs are validated before writes. Empty databases produce comment-only
configs; an empty Bazel graph is a successful no-op. Standalone mode still requires
at least one input. Pass all databases for a folder on every run; absent folders
are untouched. Writes are not transactionally rolled back.

## Verification

From the repository root:

```sh
bazel test //tools/ccdb:generator_test //tools/ccdb:run_test --test_output=errors
bazel run //tools/ccdb/testdata:configs
bazel run //tools/ccdb/testdata:empty_configs
bazel run //tools/ccdb:abseil
bazel run @buildifier_prebuilt//:buildifier -- -mode=check tools/ccdb/BUILD tools/ccdb/ccdb.bzl tools/ccdb/testdata/BUILD tools/ccdb/testdata/sub/BUILD
git diff --check
```

The explicit runs succeeded: two configs for the local graph, no outputs for the
empty graph, and six package configs from thirteen Abseil databases. Unit and
integration tests and formatting/whitespace checks also passed during the refactor.

Standalone distribution remains supported:

```sh
bazel build //tools/ccdb:ccdb_deploy.jar
java -jar bazel-bin/tools/ccdb/ccdb_deploy.jar @inputs.rsp
```

Keep the quotes around `run_test`'s `-Dccdb.databases='$(rootpaths ...)'` JVM flag.
`RunfilesRepo.location(...)` returns a `Path`.

## Limitations and workspace

- Cross-package/generated sources outside a target's source package remain rejected.
- Shared sources in different target databases in a group remain ambiguous.
- Database command directories still depend on the original execroot remaining valid.
- The package inference assumes the database's target directory is directly below
  its package directory; target names containing slashes need additional mapping.
- Multiple configured instances with identical runfile paths can collide in Bazel's
  runfiles tree even though their actual output directories differ.
- Existing source changes and untracked test files are intentional; no staging or
  commit was requested. `modules/compilecommands.bzl` was already modified before
  this refactor and was left unchanged.
- The active writable workspace is `tools/ccdb`; Bazel needs access to its external
  output cache. Use the normal approval mechanism when sandbox permissions block it.

The sibling `sample` directory contains the earlier Python mirror prototype and
VS Code path-mapping example. Its directory wiping, symlink creation, and prefixed
source naming are separate from this Java generator.
