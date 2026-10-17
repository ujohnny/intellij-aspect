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

"""Materializes the Apple action environment on each resolved C++ toolchain."""

load("@apple_support//lib:apple_support.bzl", "apple_support")
load("@apple_support//lib:xcode_support.bzl", "xcode_support")
load("@rules_cc//cc:defs.bzl", "cc_common")
load("@rules_cc//cc:find_cc_toolchain.bzl", "CC_TOOLCHAIN_TYPE")
load("//common:provider.bzl", "intellij_provider")
load(":module.bzl", "intellij_module")

OUTPUT_GROUP = "intellij-apple-support"

def _implementation(target, ctx, attr):
    if cc_common.CcToolchainInfo not in target:
        return None
    if not apple_support.target_os_from_rule_ctx(ctx, fail_on_missing_constraint = False):
        return None

    paths = ctx.actions.declare_file(target.label.name + ".apple_support.txt")
    apple_support.run_shell(
        actions = ctx.actions,
        apple_platform_info = apple_support.platform_info_from_rule_ctx(ctx),
        xcode_config = xcode_support.get_current_xcode(ctx),
        outputs = [paths],
        arguments = [paths.path],
        command = 'printf "%s\\n" "$DEVELOPER_DIR" "$SDKROOT" > "$1"',
        mnemonic = "IntelliJAppleSupport",
        progress_message = "Resolving Apple paths for %{label}",
        # IDE paths must describe this machine, including when remote execution
        # or a shared action cache is enabled.
        execution_requirements = {"local": "1", "no-cache": "1"},
    )
    return intellij_module.result(
        None,
        cross_target_internal_value = paths,
        outputs = {OUTPUT_GROUP: depset([paths])},
    )

_aspect = intellij_module.aspect(
    provider = intellij_provider.AppleSupportInfo,
    implementation = _implementation,
)

module = intellij_module.define(
    file = "apple_support",
    aspect = _aspect,
    aspect_attrs = apple_support.action_required_attrs() | apple_support.platform_constraint_attrs(),
    toolchains = [CC_TOOLCHAIN_TYPE],
    rulesets = ["CC"],
)
