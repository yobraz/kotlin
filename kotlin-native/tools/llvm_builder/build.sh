#!/bin/bash

#
# Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
#

set -e

BRANCH=apple-20200108
VERSION=1
ARCH=x86_64
DISTRIBUTION_NAME=llvm-$ARCH-$BRANCH-$VERSION

mkdir build-$BRANCH

pushd build-$BRANCH

cmake -DLLVM_ENABLE_PROJECTS="clang;lld;libcxx;libcxxabi" \
 -DCMAKE_BUILD_TYPE=Release \
 -DLLVM_ENABLE_ASSERTIONS=Off \
 -G Ninja \
 -DLLVM_BUILD_LLVM_DYLIB=On \
 -DLLVM_LINK_LLVM_DYLIB=On \
 -DLLVM_INSTALL_TOOLCHAIN_ONLY=On \
 -DCMAKE_INSTALL_PREFIX="$DISTRIBUTION_NAME" \
 ../llvm

ninja install

popd