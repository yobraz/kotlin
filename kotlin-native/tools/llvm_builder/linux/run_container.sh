#!/bin/bash
#
# Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
#

set -eou pipefail

CONTAINER_NAME=kotlin-llvm-builder
IMAGE_NAME=kotlin-llvm-builder
NAME=$1

docker ps -a | grep $CONTAINER_NAME > /dev/null \
  && docker stop $CONTAINER_NAME > /dev/null \
  && docker rm $CONTAINER_NAME > /dev/null

echo "Running build script in container..."
docker run -it -v "$PWD"/artifacts:/artifacts \
  --env NAME="$NAME" \
  --name=$CONTAINER_NAME $IMAGE_NAME
echo "Done."