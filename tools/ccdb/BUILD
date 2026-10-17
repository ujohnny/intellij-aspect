load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load(":ccdb.bzl", "ccdb", "ccdb_new")

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
        "@maven//:com_google_code_gson_gson",
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
        "@maven//:com_google_code_gson_gson",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "rule_test",
    size = "small",
    srcs = ["CompileCommandsRuleTest.java"],
    data = [
        "//tools/ccdb/testdata:empty_merged",
        "//tools/ccdb/testdata:merged",
    ],
    jvm_flags = [
        "-Dccdb.merged=$(rootpath //tools/ccdb/testdata:merged)",
        "-Dccdb.empty=$(rootpath //tools/ccdb/testdata:empty_merged)",
    ],
    test_class = "com.intellij.aspect.tools.ccdb.CompileCommandsRuleTest",
    deps = [
        "//tools/lib:runfiles_repo",
        "@maven//:com_google_code_gson_gson",
        "@maven//:junit_junit",
    ],
)

ccdb(
    name = "abseil",
    deps = ["@abseil-cpp//absl/time:time"],
)

java_library(
    name = "merger",
    srcs = ["CompileCommandsMerger.java"],
    deps = ["@maven//:com_google_code_gson_gson"],
)

java_binary(
    name = "merge",
    main_class = "com.intellij.aspect.tools.ccdb.CompileCommandsMerger",
    visibility = ["//visibility:public"],
    runtime_deps = [":merger"],
)

java_test(
    name = "merger_test",
    size = "small",
    srcs = ["CompileCommandsMergerTest.java"],
    test_class = "com.intellij.aspect.tools.ccdb.CompileCommandsMergerTest",
    deps = [
        ":merger",
        "@maven//:com_google_code_gson_gson",
        "@maven//:junit_junit",
    ],
)

ccdb_new(
    name = "abseil_new",
    deps = ["@abseil-cpp//absl/time:time"],
)
