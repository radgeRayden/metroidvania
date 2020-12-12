import platform
class UnsupportedPlatform(Exception):
    pass

def module_dep(name):
    return f"./.git/modules/3rd-party/{name}/HEAD"

make_flavor = ""
make = ""

genie_url = ""
genie_name = ""

soloud_dir = "./3rd-party/soloud"
soloud_static = f"{soloud_dir}/lib/libsoloud_static_x64.a"
soloud_dynamic = ""
soloud_backends = ""

cimgui_dir = "./3rd-party/cimgui"
cimgui_static = f"{cimgui_dir}/libcimgui.a"
cimgui_dynamic = ""

glfw_dir = "./3rd-party/glfw"
glfw_build = f"{glfw_dir}/build"
glfw_static = f"{glfw_build}/src/libglfw3.a"
glfw_dynamic = ""

physfs_dir = "./3rd-party/physfs-3.0.2"
physfs_build = f"{physfs_dir}/build"
physfs_static = f"{physfs_build}/libphysfs.a"
physfs_dynamic = ""

operating_system = platform.system()
is_windows = operating_system.startswith("MINGW")
if is_windows:
    make_flavor = "MinGW"
    make = "mingw32-make"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/windows/genie.exe"
    genie_name = "genie.exe"
    soloud_dynamic = f"{soloud_dir}/lib/soloud_x64.dll"
    cimgui_dynamic = f"{cimgui_dir}/cimgui.dll"
    glfw_dynamic = f"{glfw_build}/src/glfw3.dll"
    physfs_dynamic = f"{physfs_build}/libphysfs.dll"

    soloud_backends = "--with-miniaudio --with-wasapi"
elif "Linux" in operating_system:
    make_flavor = "Unix"
    make = "make"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/linux/genie"
    genie_name = "genie"
    soloud_dynamic = f"{soloud_dir}/lib/libsoloud_x64.so"
    cimgui_dynamic = f"{cimgui_dir}/cimgui.so"
    glfw_dynamic = f"{glfw_build}/src/libglfw3.so"
    physfs_dynamic = f"{physfs_build}/libphysfs.so"

    soloud_backends = "--with-portaudio"
else:
    raise UnsupportedPlatform

def wrap_cmake(basedir, options):
    return f"mkdir -p {basedir}; cd {basedir}; cmake .. -G '{make_flavor} Makefiles' {options}"

from doit.tools import LongRunning
from doit.tools import run_once

def task_launch():
    """launch the game"""
    cmd = "scopes ./src/boot.sc"
    return {
            'actions': [LongRunning(cmd)]
        }

def task_get_genie():
    """get the genie executable to build soloud"""
    genie_path = f"./3rd-party/{genie_name}"
    dl_cmd = f"wget {genie_url} -O {genie_path}"
    chmod_cmd = f"chmod +x {genie_path}"
    return {
        'targets': [genie_path],
        'actions': [dl_cmd, chmod_cmd],
        'uptodate': [run_once]
    }

def download_soloud_dll():
    soloud_url = "http://sol.gfxile.net/soloud/soloud_20200207_lite.zip"
    dl_cmd = f"wget {soloud_url} -O {soloud_dir}/lite.zip"
    copy_cmd = f"unzip -jo {soloud_dir}/lite.zip soloud20200207/bin/soloud_x64.dll -d {soloud_dir}/lib"
    return {
        'basename': "soloud_dll",
        'actions': [dl_cmd, copy_cmd],
        'targets': [soloud_dynamic],
        'uptodate': [run_once]
    }

def build_soloud_so():
    build_dir = f"{soloud_dir}/build"
    backends = "--with-portaudio --with-nosound"
    genie_cmd = f"{genie_path} --file={build_dir}/genie.lua {backends} --platform=x64 gmake"
    make_cmd = f"make -C {build_dir}/gmake config=release64 SoloudDynamic"
    return {
        'basename': "soloud_so",
        'actions': [genie_cmd, make_cmd],
        'targets': [soloud_dynamic],
        'file_dep': [genie_path, module_dep("soloud")]
    }

def task_soloud_dynamic():
    genie_path = f"./3rd-party/{genie_name}"
    if is_windows:
        yield download_soloud_dll()
    elif "Linux" in operating_system:
        yield build_soloud_so()
    else:
        raise UnsupportedPlatform

def task_soloud():
    genie_path = f"./3rd-party/{genie_name}"
    build_dir = f"{soloud_dir}/build"
    genie_cmd = f"{genie_path} --file={build_dir}/genie.lua {soloud_backends} --with-nosound --platform=x64 gmake"
    make_cmd = f"{make} -C {build_dir}/gmake config=release64 SoloudStatic"
    return {
        'actions': [f"rm -rf {build_dir}/gmake", genie_cmd, make_cmd],
        'targets': [soloud_static],
        'file_dep': [genie_path, soloud_dynamic, module_dep("soloud")]
    }

def task_cimgui():
    cmd_static = f"{make} -C {cimgui_dir} static"
    cmd_dynamic = f"{make} -C {cimgui_dir}"
    return {
        'actions': [cmd_static, cmd_dynamic],
        'targets': [cimgui_static, cimgui_dynamic],
        'file_dep': [module_dep("cimgui")]
    }

def task_glfw():
    shared_options = "-DGLFW_BUILD_EXAMPLES=off -DGLFW_BUILD_TESTS=off -DGLFW_BUILD_DOCS=off -DBUILD_SHARED_LIBS=on"
    static_options = "-DGLFW_BUILD_EXAMPLES=off -DGLFW_BUILD_TESTS=off -DGLFW_BUILD_DOCS=off -DBUILD_SHARED_LIBS=off"
    make_cmd = f"{make} -C {glfw_build}"
    return {
        'actions': [wrap_cmake(glfw_build, shared_options), make_cmd, wrap_cmake(glfw_build, static_options), make_cmd],
        'targets': [glfw_static, glfw_dynamic],
        'file_dep': [module_dep("glfw")]
    }

def task_physfs():
    make_shared = f"{make} -C {physfs_build}"
    make_static = f"{make} -C {physfs_build} physfs-static"
    return {
        'actions': [wrap_cmake(physfs_build, ""), make_shared, make_static],
        'targets': [physfs_static, physfs_dynamic],
        'uptodate': [True]
    }