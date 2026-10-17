# Copyright 2026 JetBrains s.r.o.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Collects compilation databases and merges them with local toolchain paths."""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@rules_java//java:defs.bzl", "java_binary")
load("//common:provider.bzl", "intellij_provider")
load("//intellij:aspect.bzl", "intellij_configure_aspect")
load("//modules:apple_support.bzl", APPLE_OUTPUT_GROUP = "OUTPUT_GROUP", apple_support_info = "module")
load("//modules:cc_info.bzl", cc_info = "module")
load("//modules:cc_toolchain_info.bzl", cc_toolchain_info = "module")
load("//modules:compilecommands.bzl", "OUTPUT_GROUP", compilecommands_info = "module")
load("//modules:module.bzl", "intellij_module")

compilecommands_aspect = intellij_configure_aspect(
    modules = [cc_info, cc_toolchain_info, apple_support_info, compilecommands_info],
    container = Label("//:module_container"),
)

def _compile_commands_impl(ctx):
    databases = depset(transitive = [
        getattr(dep[OutputGroupInfo], OUTPUT_GROUP, depset())
        for dep in ctx.attr.deps
        if OutputGroupInfo in dep
    ])
    return [
        DefaultInfo(files = databases, runfiles = ctx.runfiles(transitive_files = databases)),
        # Unlike $(rootpaths), this also expands successfully for an empty graph.
        platform_common.TemplateVariableInfo({
            "CCDB_DATABASES": " ".join([shell.quote(path) for path in sorted([
                database.short_path
                for database in databases.to_list()
            ])]),
        }),
    ]

_compile_commands = rule(
    implementation = _compile_commands_impl,
    attrs = {
        "deps": attr.label_list(
            mandatory = True,
            aspects = [compilecommands_aspect],
        ),
    },
)

def ccdb(name, deps, **kwargs):
    """Creates a Java executable with every transitive compilation database in args.

    Args:
        name: Name of the executable to invoke with bazel run.
        deps: C++ targets whose transitive compilation databases should be mapped.
        **kwargs: Additional java_binary attributes, such as visibility and testonly.
    """
    databases = name + "_compile_commands"
    _compile_commands(
        name = databases,
        deps = deps,
        visibility = kwargs.get("visibility", ["//visibility:private"]),
        **{key: kwargs[key] for key in ["testonly"] if key in kwargs}
    )
    java_binary(
        name = name,
        main_class = "com.intellij.aspect.tools.ccdb.ClangdGenerator",
        runtime_deps = [Label("//tools/ccdb:generator")],
        data = [":" + databases],
        toolchains = [":" + databases],
        args = ["--bazel-runfiles", "$(CCDB_DATABASES)"],
        **kwargs
    )

def _merge_compile_commands(ctx, clangd = None):
    bindings = []
    for dep in ctx.attr.deps:
        info = intellij_module.lookup_target(dep, intellij_provider.CompileCommandsInfo)
        if info:
            bindings.append(info.databases)
    manifest = ctx.actions.declare_file(ctx.label.name + "/apple_support.json")
    ctx.actions.write(manifest, json.encode({
        entry.database.path: entry.apple_paths.path
        for entry in depset(transitive = bindings).to_list()
        if entry.apple_paths
    }))
    inputs = depset(transitive = [
        getattr(dep[OutputGroupInfo], group, depset())
        for dep in ctx.attr.deps
        if OutputGroupInfo in dep
        for group in [OUTPUT_GROUP, APPLE_OUTPUT_GROUP]
    ])
    output = ctx.actions.declare_file(ctx.label.name + "/compile_commands.json")
    outputs = [output] + ([clangd] if clangd else [])
    args = ctx.actions.args()
    if clangd:
        args.add("--clangd")
        args.add(clangd)
    args.add(output)
    args.add(manifest)
    args.add_all(inputs)
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")
    ctx.actions.run(
        executable = ctx.executable._merger,
        arguments = [args],
        inputs = depset([manifest], transitive = [inputs]),
        outputs = outputs,
        mnemonic = "IntelliJCompileCommandsMerge",
        progress_message = "Merging compile commands for %{label}",
        # The process working directory is the actual local execroot. Do not
        # reuse absolute paths from another workspace or remote executor.
        execution_requirements = {"local": "1", "no-cache": "1"},
    )
    return [DefaultInfo(files = depset(outputs), runfiles = ctx.runfiles(files = outputs))]

def _ccdb_new_impl(ctx):
    return _merge_compile_commands(ctx)

def _ccdb_clangd_impl(ctx):
    # Each package owns its config; never write into a dependency's package.
    return _merge_compile_commands(ctx, clangd = ctx.actions.declare_file(".clangd"))

def _ccdb_attrs():
    return {
        "deps": attr.label_list(
            mandatory = True,
            aspects = [compilecommands_aspect],
        ),
        "_merger": attr.label(
            default = Label("//tools/ccdb:merge"),
            executable = True,
            cfg = "exec",
        ),
    }

ccdb_new = rule(
    implementation = _ccdb_new_impl,
    doc = "Merges transitive compile commands and resolves execroot and Apple placeholders.",
    attrs = _ccdb_attrs(),
)

ccdb_clangd = rule(
    implementation = _ccdb_clangd_impl,
    doc = "Builds a package-local .clangd and merged database. Define at most once per package.",
    attrs = _ccdb_attrs(),
)
