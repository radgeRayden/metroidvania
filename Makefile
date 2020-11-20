INCLUDE_DIRS = ./3rd-party/glad/include ./3rd-party/cimgui/imgui
IFLAGS = $(addprefix -I, $(INCLUDE_DIRS))
CFLAGS = -Wall -O2 -fPIC $(IFLAGS)
CXXFLAGS = $(CFLAGS) -DIMGUI_IMPL_API="extern \"C\"" -DIMGUI_IMPL_OPENGL_LOADER_GLAD
LFLAGS =

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S), Linux)
	ECHO_MESSAGE = "Linux"
	MAKEFILE_FLAVOR = Unix
	MAKE = make
	LIBGAME_SHARED = libgame.so
	PHYSFS_SHARED = libphysfs.so
	GLFW_SHARED = libglfw.so
	CIMGUI_SHARED = cimgui.so
endif

ifeq ($(OS), Windows_NT)
	ECHO_MESSAGE = "Windows"
	MAKEFILE_FLAVOR = MinGW
	CC = x86_64-w64-mingw32-gcc
	CXX = x86_64-w64-mingw32-g++
	MAKE = mingw32-make
	LFLAGS += -Wl,--export-all
	LIBGAME_SHARED = libgame.dll
	PHYSFS_SHARED = libphysfs.dll
	GLFW_SHARED = glfw3.dll
	CIMGUI_SHARED = cimgui.dll
endif

SHARED_LIBS = $(addprefix ./lib/, $(LIBGAME_SHARED) $(PHYSFS_SHARED) $(GLFW_SHARED) $(CIMGUI_SHARED))

all:$(SHARED_LIBS)
	@echo "Build complete."

.cpp.o:
	$(CXX) $(CXXFLAGS) -c -o $@ $<

LIBGAME_DEPS += ./3rd-party/glad/src/glad.o
LIBGAME_DEPS += ./3rd-party/cJSON/cJSON.o
LIBGAME_DEPS += ./3rd-party/stb.o
LIBGAME_DEPS += ./3rd-party/cute.o
LIBGAME_DEPS += ./3rd-party/cimgui/imgui/examples/imgui_impl_opengl3.o
LIBGAME_DEPS += ./3rd-party/cimgui/imgui/examples/imgui_impl_glfw.o

lib/$(LIBGAME_SHARED):$(LIBGAME_DEPS)
	mkdir -p ./lib
	$(CC) -o ./lib/$(LIBGAME_SHARED) $(LIBGAME_DEPS) -shared $(CFLAGS) $(LFLAGS)

PHYSFS_SRC = ./3rd-party/physfs-3.0.2
PHYSFS_BUILD = $(PHYSFS_SRC)/build
lib/$(PHYSFS_SHARED):
	mkdir -p ./lib
	mkdir -p $(PHYSFS_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" -S $(PHYSFS_SRC) -B $(PHYSFS_BUILD)
	${MAKE} -C $(PHYSFS_BUILD)
	cp $(shell realpath $(PHYSFS_BUILD)/$(PHYSFS_SHARED)) ./lib/$(PHYSFS_SHARED)

GLFW_SRC = ./3rd-party/glfw
GLFW_BUILD = $(GLFW_SRC)/build
lib/$(GLFW_SHARED):
	mkdir -p ./lib
	mkdir -p $(GLFW_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" -S $(GLFW_SRC) -B $(GLFW_BUILD)
	${MAKE} -C $(GLFW_BUILD)
	cp $(shell realpath $(GLFW_BUILD)/src/$(GLFW_SHARED)) ./lib/$(GLFW_SHARED)

CIMGUI_SRC = ./3rd-party/cimgui
CIMGUI_BUILD = $(CIMGUI_SRC)/build
lib/$(CIMGUI_SHARED):
	mkdir -p ./lib
	mkdir -p $(CIMGUI_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" -S $(CIMGUI_SRC) -B $(CIMGUI_BUILD)
	${MAKE} -C $(CIMGUI_BUILD)
	cp $(shell realpath $(CIMGUI_BUILD)/$(CIMGUI_SHARED)) ./lib/$(CIMGUI_SHARED)

clean:
	${MAKE} -C $(PHYSFS_BUILD) clean
	${MAKE} -C $(GLFW_BUILD) clean
	rm -f $(LIBGAME_DEPS)
	rm -rf ./lib

.PHONY: all clean
