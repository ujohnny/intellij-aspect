load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("//tools/ccdb:ccdb.bzl", "ccdb")

package(default_testonly = True)

cc_library(
    name = "alpha",
    srcs = ["alpha.cc"],
    defines = ["TARGET_ALPHA"],
    deps = ["//tools/ccdb/testdata/sub:library"],
)

cc_library(
    name = "beta",
    srcs = ["beta.cc"],
    defines = ["TARGET_BETA"],
    deps = ["//tools/ccdb/testdata/sub:library"],
)

cc_library(name = "headers_only")

ccdb(
    name = "configs",
    visibility = ["//tools/ccdb:__pkg__"],
    deps = [
        ":alpha",
        ":beta",
    ],
)

ccdb(
    name = "empty_configs",
    visibility = ["//tools/ccdb:__pkg__"],
    deps = [":headers_only"],
)
