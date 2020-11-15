#!/usr/bin/bash
gcc -o ./lib/libgame.so ./3rd-party/glad/src/glad.c ./3rd-party/cJSON/cJSON.c ./3rd-party/stb.c -O2 -shared -fPIC -I./src/FFI/glad/include

PHYSFS_ROOT="./3rd-party/physfs-3.0.2"
mkdir -p $PHYSFS_ROOT/build
cmake -G "Unix Makefiles" -DCMAKE_C_COMPILER=gcc -DCMAKE_C_FLAGS=-fPIC -S $PHYSFS_ROOT -B $PHYSFS_ROOT/build
make -C $PHYSFS_ROOT/build
