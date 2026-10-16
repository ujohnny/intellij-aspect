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

"""Runs the clangd generator explicitly over transitive C++ aspect outputs."""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@rules_java//java:defs.bzl", "java_binary")
load("//intellij:aspect.bzl", "intellij_configure_aspect")
load("//modules:cc_info.bzl", cc_info = "module")
load("//modules:cc_toolchain_info.bzl", cc_toolchain_info = "module")
load("//modules:compilecommands.bzl", "OUTPUT_GROUP", compilecommands_info = "module")

compilecommands_aspect = intellij_configure_aspect(
    modules = [cc_info, cc_toolchain_info, compilecommands_info],
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
