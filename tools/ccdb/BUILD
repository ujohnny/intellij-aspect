load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")

java_library(
    name = "generator",
    srcs = [
        "ClangdGenerator.java",
        "ResponseFiles.java",
    ],
    deps = ["@maven//:com_google_code_gson_gson"],
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
