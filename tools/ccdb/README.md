# ccdb: standalone .clangd generator

Java 21, built with the repository's existing Bazel toolchain. Takes paths to existing `compile_commands.json` files
and generates one `.clangd` in the parent of their target directories:

```text
compdb/src/
  .clangd                     # generated
  targetA_foo.cpp
  targetB_baz.cpp
  targetA/compile_commands.json  # input
  targetB/compile_commands.json  # input
```

Databases in `compdb/extra/<target>/` produce `compdb/extra/.clangd`. There is no
aggregate `compdb/.clangd`. Target names come from database directories; source
filenames need no special naming convention.

## Build and run

From the repository root:

```sh
bazel test //tools/ccdb:generator_test
bazel run //tools/ccdb:ccdb -- \
  /path/to/compdb/src/targetA/compile_commands.json \
  /path/to/compdb/src/targetB/compile_commands.json
```

The tool uses the repository's Java 21 configuration and existing Gson/JUnit
Maven dependencies. To distribute a single standalone JAR:

```sh
bazel build //tools/ccdb:ccdb_deploy.jar
java -jar bazel-bin/tools/ccdb/ccdb_deploy.jar @inputs.rsp
```

The JAR includes Gson and requires Java 21 or newer. It does not require Bazel at
runtime. Direct arguments resolve relative to the invocation directory, including
when invoked with `bazel run`.

## Response files

Mix direct database paths with `@response-file` arguments:

```sh
java -jar bazel-bin/tools/ccdb/ccdb_deploy.jar @inputs.rsp
```

Example `inputs.rsp` alongside a `compdb/` directory:

```text
# Whitespace separates paths; quote paths containing spaces.
compdb/src/targetA/compile_commands.json
"compdb/src/targetB/compile_commands.json"
compdb/extra/targetC/compile_commands.json
```

Response files are UTF-8. They accept single/double quotes, backslash escapes,
comments beginning with `#` at the start of a token, and nested `@files`. Paths
inside a response file resolve relative to that file's directory. There is no
shell variable or glob expansion. Use forward slashes or single quotes for
Windows paths. Cycles and nesting deeper than 64 files are rejected.

## Mapping and output

Each entry's `file` is resolved against its `directory`. Absolute paths work;
relative `directory` values resolve against the database's directory. Symlinks
are not resolved, so mirrored source paths remain in `compdb`. Sources must be
under the folder receiving `.clangd`; otherwise its configuration could not
apply to them and the tool reports an error. Source files need not exist yet.

For the layout above:

```yaml
If:
  PathMatch:
    - "targetA_foo\\.cpp"
CompileFlags:
  CompilationDatabase: "targetA"
---
If:
  PathMatch:
    - "targetB_baz\\.cpp"
CompileFlags:
  CompilationDatabase: "targetB"
```

Paths are relative, regex metacharacters are escaped, and YAML strings are
JSON-quoted. Targets and sources are sorted for deterministic output. Duplicate
inputs and repeated commands within a database are deduplicated. A source mapped
to different databases is rejected rather than silently selecting one.

The tool overwrites `.clangd` only in the grouped folders; pass **all** databases
for each folder on each invocation. Empty databases contribute no fragment; a
folder with only empty databases gets a comment-only config, clearing old
mappings. Configurations in folders absent from the input are left untouched.
All inputs are validated before writing. Databases and sources are never changed;
the tool does not create symlinks or wipe directories.

Use `--help` for usage. Invalid input exits with status 1. To edit original
sources while retaining a mirror, configure clangd with
`--path-mappings=/absolute/project=/absolute/compdb`.

## Legacy Bazel macro

The old `ccdb` macro is retained as a prototype, but no longer resolves aspect
placeholders. Use `ccdb_new` below for Bazel-generated compilation databases.
The standalone `.clangd` CLI above still accepts already resolved databases.

## Merged compilation database: ccdb_new

```starlark
load("//tools/ccdb:ccdb.bzl", "ccdb_new")

ccdb_new(
    name = "compilation_database",
    deps = ["//app:main", "//lib:core"],
)
```

```sh
bazel build //:compilation_database
# Output: bazel-bin/compilation_database/compile_commands.json
# Included example:
bazel build //tools/ccdb:abseil_new
```

`ccdb_new` applies the IntelliJ aspect to `deps`, collects the transitive
`intellij-compilecommands` output group, and runs a Java merger as a build action.
`DefaultInfo.files` contains exactly one merged `compile_commands.json`. An empty
or headers-only graph produces `[]`. Shared input files are included once; distinct
compile commands for the same source are preserved. Inputs are sorted by path for
stable ordering.

The aspect writes each target's database as `compile_commands_<target-name>.json`
in its package's output directory, without shell processing. This avoids collisions
with executable outputs named after the target. On Apple target
platforms, the separate `modules/apple_support.bzl` module runs
`apple_support.run_shell` on the resolved C++ toolchain and contributes a
`<toolchain>.apple_support.txt` to the same output group. This UTF-8 file contains
exactly two lines: `DEVELOPER_DIR` and `SDKROOT`. Non-Apple toolchains contribute no
Apple file.

Intermediate command entries carry an optional `_apple_support` filename linking
them to their own toolchain's data. The merger removes this private field and
replaces `{EXECROOT}`, `__BAZEL_EXECUTION_ROOT__`,
`__BAZEL_EXECUTION_ROOT_CANONICAL__`, `__BAZEL_XCODE_DEVELOPER_DIR__`, and
`__BAZEL_XCODE_SDKROOT__` in JSON string values. Different toolchains can therefore
use different SDKs in one database. Missing Apple data is an error; JSON escaping
is preserved when paths contain spaces, quotes, or backslashes.

Both environment discovery and merging run locally without cached action results,
so IDE paths refer to the current machine and execroot. This requires a local
Apple execution environment when generating commands for Apple targets. The rule
does not compile the C++ targets or materialize their generated source files.

The Java entry point is `//tools/ccdb:merge` (Java 21, Gson). It accepts the output
path followed by all database and Apple-data paths, or one `@params` argument with
one literal path per line. Relative paths and `_apple_support` references are
relative to its working directory, which is the execroot in the Bazel action.

```sh
bazel test //tools/ccdb:merger_test //tools/ccdb:rule_test //tools/ccdb:generator_test
```
