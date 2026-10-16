load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load(":ccdb.bzl", "ccdb")

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
    name = "run_test",
    size = "small",
    srcs = ["BazelRunTest.java"],
    data = [
        "//tools/ccdb/testdata:configs",
        "//tools/ccdb/testdata:configs_compile_commands",
        "//tools/ccdb/testdata:empty_configs",
    ],
    jvm_flags = [
        "-Dccdb.databases='$(rootpaths //tools/ccdb/testdata:configs_compile_commands)'",
    ],
    test_class = "com.intellij.aspect.tools.ccdb.BazelRunTest",
    deps = [
        ":generator",
        "//tools/lib:runfiles_repo",
        "@maven//:junit_junit",
    ],
)

ccdb(
    name = "abseil",
    deps = ["@abseil-cpp//absl/time:time"],
)
