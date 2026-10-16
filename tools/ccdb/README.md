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

## Bazel run

```starlark
load("//tools/ccdb:ccdb.bzl", "ccdb")

ccdb(
    name = "clangd_configs",
    deps = ["//app:main", "//lib:core"],
)
```

Run the generator explicitly:

```sh
bazel run //:clangd_configs
# Included example:
bazel run //tools/ccdb:abseil
```

`ccdb` is a macro around `java_binary`. A collection-only helper applies the
IntelliJ aspect to `deps` and collects every transitive `intellij-compilecommands`
output. The Java executable receives these database runfile paths through its
`args`, using `--bazel-runfiles`. Empty dependency graphs are a successful no-op.

Generation happens during `bazel run`, outside the build action sandbox. A build
alone prepares the executable and databases; it does not generate `.clangd`
files. There is no generator action or manifest, and the configs are not declared
Bazel outputs.

The tool resolves database runfile symlinks and writes one `.clangd` beside each
group of target database directories, for example:

```text
bazel-out/<configuration>/bin/app/
  .clangd
  main/compile_commands.json
  helper/compile_commands.json
```

External repositories stay under `bin/external/<repo>/`, and configurations remain
separate. Database references are relative (`main`, `helper`); source matches are
relative to the original source package, using the execroot recorded in the
commands. Original source packages are not modified. Configs under `bazel-out`
need a matching source layout or path mapping for clangd to discover and apply
them. Copying a config elsewhere also requires adjusting its relative database
references.

Rerun with all dependencies for each package to refresh its mapping. Shared sources
assigned to different target databases in the same package/configuration are
rejected as ambiguous. The standalone CLI and response-file support are unchanged.

Run the unit and integration tests with:

```sh
bazel test //tools/ccdb:generator_test //tools/ccdb:run_test
```
