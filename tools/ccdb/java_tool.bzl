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

"""Runs an internal Java tool with Java 21 independently of consumer flags."""

def _java21_transition_impl(_settings, _attr):
    return {
        "//command_line_option:java_language_version": "21",
        "//command_line_option:java_runtime_version": "remotejdk_21",
    }

_java21_transition = transition(
    implementation = _java21_transition_impl,
    inputs = [],
    outputs = [
        "//command_line_option:java_language_version",
        "//command_line_option:java_runtime_version",
    ],
)

def _java21_tool_impl(ctx):
    binary = ctx.attr.binary[DefaultInfo]
    executable = ctx.actions.declare_file(ctx.label.name)
    ctx.actions.symlink(
        output = executable,
        target_file = ctx.executable.binary,
        is_executable = True,
    )
    return [DefaultInfo(
        executable = executable,
        runfiles = ctx.runfiles(files = [ctx.executable.binary]).merge(binary.default_runfiles),
    )]

java21_tool = rule(
    implementation = _java21_tool_impl,
    executable = True,
    # Apply after the caller's exec transition so its selected platform is retained.
    cfg = _java21_transition,
    attrs = {
        "binary": attr.label(mandatory = True, executable = True, cfg = "target"),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)
