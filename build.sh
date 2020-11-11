#!/usr/bin/bash
gcc -o ./lib/libgame.so ./3rd-party/glad/src/glad.c -O2 -shared -fPIC -I./src/FFI/glad/include
