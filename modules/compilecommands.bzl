load("@rules_cc//cc:defs.bzl", "CcInfo")
load("@rules_cc//cc:find_cc_toolchain.bzl", "CC_TOOLCHAIN_TYPE")
load("//common:common.bzl", "intellij_common")
load("//common:provider.bzl", "intellij_provider")
load(":module.bzl", "intellij_module")

OUTPUT_GROUP = "intellij-compilecommands"

EXECROOT_PLACEHOLDER = "{EXECROOT}"

CPP_EXTENSION = ["cpp", "cc", "cxx", "c++"]

C_EXTENSION = ["c"]

def _lookup_list(info, name):
    """Since we trim providers we need getattr for lookups."""
    return getattr(info, name, [])

def _compilecommands_entry(c_arguments, cpp_arguments, info, file):
    is_cpp = file.extension in CPP_EXTENSION

    rule_ctx = info.rule_context
    comp_ctx = info.compilation_context

    arguments = list(cpp_arguments if is_cpp else c_arguments)
    arguments.extend(_lookup_list(rule_ctx, "copts"))
    arguments.extend(_lookup_list(rule_ctx, "cxxopts" if is_cpp else "conlyopts"))

    for define in _lookup_list(comp_ctx, "defines"):
        arguments.append("-D%s" % define)

    for include in _lookup_list(comp_ctx, "includes"):
        arguments.append("-I%s" % include)

    for include in _lookup_list(comp_ctx, "quote_includes"):
        arguments.append("-iquote%s" % include)

    for include in _lookup_list(comp_ctx, "system_includes"):
        arguments.append("-isystem%s" % include)

    arguments.extend(["-c", file.path])

    return intellij_common.struct(
        directory = EXECROOT_PLACEHOLDER,
        arguments = arguments,
        file = file.path,
    )

def _find_toolchain_info(ctx):
    """Looks up the toolchain info forwarded by the resolved cc toolchain."""

    # toolchains attribute only available in Bazel 8+
    toolchains = getattr(ctx.rule, "toolchains", None)
    if not toolchains or CC_TOOLCHAIN_TYPE not in toolchains:
        return None

    info = intellij_module.lookup_target(toolchains[CC_TOOLCHAIN_TYPE], intellij_provider.CompileCommandsInfo)
    return info.toolchain if info else None

def _compilecommands(target, ctx, attr):
    """Creates the commpile commands json file and writes it."""
    target_result = intellij_module.lookup_self(attr, intellij_provider.CcInfo)
    if not target_result:
        return None

    toolchain_info = _find_toolchain_info(ctx)
    if not toolchain_info:
        return None

    target_info = target_result.value
    toolchains = ctx.rule.toolchains
    apple_paths = intellij_module.lookup_target(toolchains[CC_TOOLCHAIN_TYPE], intellij_provider.AppleSupportInfo)

    c_arguments = [toolchain_info.c_compiler] + toolchain_info.c_option
    cpp_arguments = [toolchain_info.cpp_compiler] + toolchain_info.cpp_option

    for include in toolchain_info.built_in_include_directory:
        c_arguments.append("-isystem%s" % include)
        cpp_arguments.append("-isystem%s" % include)

    if getattr(toolchain_info, "sysroot", None):
        c_arguments.append("--sysroot=%s" % toolchain_info.sysroot)
        cpp_arguments.append("--sysroot=%s" % toolchain_info.sysroot)

    commands = [
        _compilecommands_entry(c_arguments, cpp_arguments, target_info, file)
        for src in intellij_common.attr_as_label_list(ctx, "srcs")
        for file in src.files.to_list()
        if file.extension in CPP_EXTENSION + C_EXTENSION
    ]

    if not commands:
        return None

    file = ctx.actions.declare_file("compile_commands_" + target.label.name + ".json")
    ctx.actions.write(file, json.encode(commands))

    return struct(database = file, apple_paths = apple_paths)

def _implementation(target, ctx, attr):
    transitive = []
    for name in dir(ctx.rule.attr):
        for dep in intellij_common.attr_as_label_list(ctx, name):
            info = intellij_module.lookup_target(dep, intellij_provider.CompileCommandsInfo)
            if info:
                transitive.append(info.databases)

    toolchain = intellij_module.lookup_self(attr, intellij_provider.CcToolchainInfo)
    database = None if toolchain else _compilecommands(target, ctx, attr)
    return intellij_module.result(
        None,
        cross_target_internal_value = struct(
            toolchain = toolchain.value if toolchain else None,
            databases = depset([database] if database else [], transitive = transitive),
        ),
        outputs = {OUTPUT_GROUP: depset([database.database])} if database else {},
    )

_aspect = intellij_module.aspect(
    provider = intellij_provider.CompileCommandsInfo,
    implementation = _implementation,
    field = "compilecommands",  # unused, does not really matter
)

module = intellij_module.define(
    file = "compilecommands",
    aspect = _aspect,
    toolchains = [CC_TOOLCHAIN_TYPE],
    aspect_providers = [CcInfo],
    rulesets = ["CC"],
)
