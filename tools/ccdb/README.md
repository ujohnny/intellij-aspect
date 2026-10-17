# ccdb: compilation databases and .clangd

## Package-local build rule: ccdb_clangd

Define one `ccdb_clangd` in each package that needs a config, listing that package's
C++ targets. Private targets are accessible from the rule in their own package.

```starlark
load("//tools/ccdb:ccdb.bzl", "ccdb_clangd")

ccdb_clangd(
    name = "clangd",
    deps = [":main", ":private_library"],
)
```

```sh
bazel build //app:clangd //lib:clangd
# Included example:
bazel build //tools/ccdb:abseil_clangd
```

The rule extends `ccdb_new`'s merge pipeline. A single Java execution action resolves
execroot and Apple toolchain placeholders and writes two declared build outputs:

```text
bazel-bin/app/
  .clangd
  clangd/compile_commands.json
```

The config selects the merged database for the package, including headers:

```yaml
CompileFlags:
  CompilationDatabase: "clangd"
```

`CompilationDatabase` is relative to `.clangd`, as specified by the
[clangd configuration reference](https://clangd.llvm.org/config#compilationdatabase).
The merged database contains commands from `deps` and their transitive dependencies,
with the same deduplication, placeholder handling, and local execution requirements
as `ccdb_new`. Empty or headers-only graphs produce `[]` and a valid config.
`DefaultInfo.files` and runfiles include both outputs.

Only the rule's own package receives a `.clangd`; dependency packages need their
own rule if they also need a config. Define at most one `ccdb_clangd` per package,
since all instances would own the same `.clangd` output. Private C++ targets do not
need broader visibility. `bazel build` creates both files without a `bazel run` step
or undeclared writes into other packages.

Configs live in the build output tree. Clangd must see them in a corresponding
source mirror or through your existing path-mapping setup; this rule does not install
configs into source directories or materialize generated sources.

```sh
bazel test //tools/ccdb:clangd_rule_test //tools/ccdb:merger_test
```

## Standalone .clangd generator

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
placeholders. Use `ccdb_clangd` above for build-time configs or `ccdb_new` below for
a merged database alone.
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
`<toolchain>.apple_support.txt` to the separate `intellij-apple-support` output group.
This UTF-8 file contains
exactly two lines: `DEVELOPER_DIR` and `SDKROOT`. Non-Apple toolchains contribute no
Apple file.

The aspect forwards each database's toolchain association through its provider.
The rule collects these associations from `deps` and writes one `apple_support.json`
manifest mapping database paths to Apple-data paths. It passes the manifest and both
output groups to the merger as separate inputs. Intermediate command entries contain
only `directory`, `file`, and `arguments`.

The merger uses the manifest to replace `{EXECROOT}`, `__BAZEL_EXECUTION_ROOT__`,
`__BAZEL_EXECUTION_ROOT_CANONICAL__`, `__BAZEL_XCODE_DEVELOPER_DIR__`, and
`__BAZEL_XCODE_SDKROOT__` in JSON string values. Different toolchains can therefore
use different SDKs in one database. Missing Apple data is an error; JSON escaping
is preserved when paths contain spaces, quotes, or backslashes.

Both environment discovery and merging run locally without cached action results,
so IDE paths refer to the current machine and execroot. This requires a local
Apple execution environment when generating commands for Apple targets. The rule
does not compile the C++ targets or materialize their generated source files.

The Java entry point is `//tools/ccdb:merge` (Java 21, Gson). It accepts an optional
`--clangd <config-output>` prefix (used by `ccdb_clangd`), followed by the database output
path, the Apple support manifest, then all database and Apple-data paths, or one
`@params` argument with one literal path per line. Relative paths in the manifest are
relative to its working directory, which is the execroot in the Bazel action.

```sh
bazel test //tools/ccdb:merger_test //tools/ccdb:rule_test //tools/ccdb:generator_test
```
