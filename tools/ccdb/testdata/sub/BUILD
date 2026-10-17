load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("//tools/ccdb:ccdb.bzl", "ccdb_clangd")

cc_library(
    name = "library",
    testonly = True,
    srcs = ["library.cc"],
    visibility = ["//tools/ccdb/testdata:__pkg__"],
)

cc_library(
    name = "private_library",
    testonly = True,
    srcs = ["library.cc"],
    defines = ["PRIVATE_LIBRARY"],
    visibility = ["//visibility:private"],
)

ccdb_clangd(
    name = "clangd",
    testonly = True,
    visibility = ["//tools/ccdb:__pkg__"],
    deps = [":private_library"],
)
