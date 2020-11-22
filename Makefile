INCLUDE_DIRS = ./3rd-party/glad/include ./3rd-party/cimgui/imgui
IFLAGS = $(addprefix -I, $(INCLUDE_DIRS))
CFLAGS = -Wall -O2 -fPIC $(IFLAGS)
CXXFLAGS = $(CFLAGS) -DIMGUI_IMPL_API="extern \"C\"" -DIMGUI_IMPL_OPENGL_LOADER_GLAD

PHYSFS_SRC = ./3rd-party/physfs-3.0.2
PHYSFS_BUILD = $(PHYSFS_SRC)/build
GLFW_SRC = ./3rd-party/glfw
GLFW_BUILD = $(GLFW_SRC)/build
CIMGUI_SRC = ./3rd-party/cimgui
CIMGUI_BUILD = $(CIMGUI_SRC)/build

CIMGUI_STATIC = $(CIMGUI_BUILD)/cimgui.a
PHYSFS_STATIC = $(PHYSFS_BUILD)/libphysfs.a
GLFW_STATIC = $(GLFW_BUILD)/src/libglfw3.a

LIBGAME_DEPS += ./3rd-party/glad/src/glad.o
LIBGAME_DEPS += ./3rd-party/cJSON/cJSON.o
LIBGAME_DEPS += ./3rd-party/stb.o
LIBGAME_DEPS += ./3rd-party/cute.o
LIBGAME_DEPS += ./3rd-party/cimgui/imgui/examples/imgui_impl_opengl3.o
LIBGAME_DEPS += ./3rd-party/cimgui/imgui/examples/imgui_impl_glfw.o
STATIC_LIBS = $(CIMGUI_STATIC) $(PHYSFS_STATIC) $(GLFW_STATIC)

MAIN_OBJ = game.o

LFLAGS = -Wl,-rpath='$$ORIGIN' -L. -Wl,--whole-archive $(addprefix -l:, $(STATIC_LIBS)) -Wl,--no-whole-archive -lpthread -lm -L./bin -lscopesrt 

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S), Linux)
	ECHO_MESSAGE = "Linux"
	MAKEFILE_FLAVOR = Unix
	MAKE = make
	LIBGAME_SHARED = libgame.so
	PHYSFS_SHARED = libphysfs.so
	GLFW_SHARED = libglfw.so
	CIMGUI_SHARED = cimgui.so
	LFLAGS += -ldl -lX11
endif

ifeq ($(OS), Windows_NT)
	ECHO_MESSAGE = "Windows"
	MAKEFILE_FLAVOR = MinGW
	CC = x86_64-w64-mingw32-gcc
	CXX = x86_64-w64-mingw32-g++
	MAKE = mingw32-make
	LIBGAME_SHARED = libgame.dll
	PHYSFS_SHARED = libphysfs.dll
	GLFW_SHARED = glfw3.dll
	CIMGUI_SHARED = cimgui.dll
	LFLAGS += -Wl,--export-all -lgdi32
endif

SHARED_LIBS = $(addprefix ./lib/, $(LIBGAME_SHARED) $(PHYSFS_SHARED) $(GLFW_SHARED) $(CIMGUI_SHARED))

all:$(SHARED_LIBS)
	@echo "Build complete."

$(MAIN_OBJ):
	scopes makemain.sc

amalgamated: $(STATIC_LIBS) $(LIBGAME_DEPS) $(MAIN_OBJ)
	mkdir -p ./bin
	scopes copyscdeps.sc
	$(CXX) -g -o ./bin/game $(LIBGAME_DEPS) game.o $(LFLAGS) 

$(CIMGUI_STATIC):
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" -DIMGUI_STATIC=on -S $(CIMGUI_SRC) -B $(CIMGUI_BUILD)
	${MAKE} -C $(CIMGUI_BUILD)

$(PHYSFS_STATIC):./lib/$(PHYSFS_SHARED)

GLFW_OPTIONS = -DGLFW_BUILD_EXAMPLES=off -DGLFW_BUILD_TESTS=off -DGLFW_BUILD_DOCS=off
$(GLFW_STATIC):
	mkdir -p ./lib
	mkdir -p $(GLFW_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" $(GLFW_OPTIONS) -DBUILD_SHARED_LIBS=off -S $(GLFW_SRC) -B $(GLFW_BUILD)
	${MAKE} -C $(GLFW_BUILD)

.cpp.o:
	$(CXX) $(CXXFLAGS) -c -o $@ $<

lib/$(LIBGAME_SHARED):$(LIBGAME_DEPS)
	mkdir -p ./lib
	$(CC) -o ./lib/$(LIBGAME_SHARED) $(LIBGAME_DEPS) -shared $(CFLAGS) -Wl,--export-all

lib/$(PHYSFS_SHARED):
	mkdir -p ./lib
	mkdir -p $(PHYSFS_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" -S $(PHYSFS_SRC) -B $(PHYSFS_BUILD)
	${MAKE} -C $(PHYSFS_BUILD)
	cp $(shell realpath $(PHYSFS_BUILD)/$(PHYSFS_SHARED)) ./lib/$(PHYSFS_SHARED)

lib/$(GLFW_SHARED):
	mkdir -p ./lib
	mkdir -p $(GLFW_BUILD)
	cmake -G "$(MAKEFILE_FLAVOR) Makefiles" -DCMAKE_C_COMPILER=$(CC) -DCMAKE_C_FLAGS="$(CFLAGS)" $(GLFW_OPTIONS) -DBUILD_SHARED_LIBS=on -S $(GLFW_SRC) -B $(GLFW_BUILD)
	${MAKE} -C $(GLFW_BUILD)
	cp $(shell realpath $(GLFW_BUILD)/src/$(GLFW_SHARED)) ./lib/$(GLFW_SHARED)

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
