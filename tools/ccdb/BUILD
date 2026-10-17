load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load(":ccdb.bzl", "ccdb", "ccdb_clangd", "ccdb_new")
load(":java_tool.bzl", "java21_tool")

java_library(
    name = "generator",
    srcs = [
        "BazelConfigGenerator.java",
        "ClangdGenerator.java",
        "ResponseFiles.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/lib:runfiles_repo",
        "@intellij_aspect_sdk_maven//:com_google_code_gson_gson",
    ],
)

java_binary(
    name = "ccdb",
    main_class = "com.intellij.aspect.tools.ccdb.ClangdGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [":generator"],
)

java_test(
    name = "generator_test",
    size = "small",
    srcs = ["ClangdGeneratorTest.java"],
    test_class = "com.intellij.aspect.tools.ccdb.ClangdGeneratorTest",
    deps = [
        ":generator",
        "@intellij_aspect_sdk_maven//:com_google_code_gson_gson",
        "@intellij_aspect_sdk_maven//:junit_junit",
    ],
)

java_test(
    name = "rule_test",
    size = "small",
    srcs = ["CompileCommandsRuleTest.java"],
    data = [
        "//tools/ccdb/testdata:configs_compile_commands",
        "//tools/ccdb/testdata:empty_merged",
        "//tools/ccdb/testdata:merged",
    ],
    jvm_flags = [
        "-Dccdb.databases='$(rootpaths //tools/ccdb/testdata:configs_compile_commands)'",
        "-Dccdb.merged=$(rootpath //tools/ccdb/testdata:merged)",
        "-Dccdb.empty=$(rootpath //tools/ccdb/testdata:empty_merged)",
    ],
    test_class = "com.intellij.aspect.tools.ccdb.CompileCommandsRuleTest",
    deps = [
        "//tools/lib:runfiles_repo",
        "@intellij_aspect_sdk_maven//:com_google_code_gson_gson",
        "@intellij_aspect_sdk_maven//:junit_junit",
    ],
)

ccdb(
    name = "abseil",
    deps = ["@abseil-cpp//absl/time:time"],
)

java_library(
    name = "merger",
    srcs = ["CompileCommandsMerger.java"],
    deps = ["@intellij_aspect_sdk_maven//:com_google_code_gson_gson"],
)

java_binary(
    name = "merge_binary",
    main_class = "com.intellij.aspect.tools.ccdb.CompileCommandsMerger",
    runtime_deps = [":merger"],
)

java21_tool(
    name = "merge",
    binary = ":merge_binary",
    visibility = ["//visibility:public"],
)

java_test(
    name = "merger_test",
    size = "small",
    srcs = ["CompileCommandsMergerTest.java"],
    test_class = "com.intellij.aspect.tools.ccdb.CompileCommandsMergerTest",
    deps = [
        ":merger",
        "@intellij_aspect_sdk_maven//:com_google_code_gson_gson",
        "@intellij_aspect_sdk_maven//:junit_junit",
    ],
)

ccdb_new(
    name = "abseil_new",
    deps = ["@abseil-cpp//absl/time:time"],
)

ccdb_clangd(
    name = "abseil_clangd",
    deps = ["@abseil-cpp//absl/time:time"],
)

java_test(
    name = "clangd_rule_test",
    size = "small",
    srcs = ["ClangdRuleTest.java"],
    data = [
        "//tools/ccdb/testdata:clangd",
        "//tools/ccdb/testdata/empty:clangd",
        "//tools/ccdb/testdata/sub:clangd",
    ],
    jvm_flags = [
        "-Dccdb.configs='$(rootpaths //tools/ccdb/testdata:clangd)'",
        "-Dccdb.empty_configs='$(rootpaths //tools/ccdb/testdata/empty:clangd)'",
        "-Dccdb.private_configs='$(rootpaths //tools/ccdb/testdata/sub:clangd)'",
    ],
    test_class = "com.intellij.aspect.tools.ccdb.ClangdRuleTest",
    deps = [
        "//tools/lib:runfiles_repo",
        "@intellij_aspect_sdk_maven//:com_google_code_gson_gson",
        "@intellij_aspect_sdk_maven//:junit_junit",
    ],
)
