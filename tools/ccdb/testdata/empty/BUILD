load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("//tools/ccdb:ccdb.bzl", "ccdb_clangd")

package(default_testonly = True)

cc_library(name = "headers_only")

ccdb_clangd(
    name = "clangd",
    visibility = ["//tools/ccdb:__pkg__"],
    deps = [":headers_only"],
)
