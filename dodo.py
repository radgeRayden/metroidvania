import platform
class UnsupportedPlatform(Exception):
    pass

def module_dep(name):
    return f"./.git/modules/3rd-party/{name}/HEAD"

make_flavor = ""

genie_url = ""
genie_name = ""

soloud_dir = "./3rd-party/soloud"
soloud_static = f"{soloud_dir}/lib/libsoloud_static_x64.a"
soloud_dynamic = ""

cimgui_dir = "./3rd-party/cimgui"
cimgui_build = f"{cimgui_dir}/build"
cimgui_static = f"{cimgui_build}/cimgui.a"

operating_system = platform.system()
if "Windows" in operating_system:
    make_flavor = "MinGW"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/windows/genie.exe"
    genie_name = "genie.exe"
    soloud_dynamic = f"{soloud_dir}/lib/soloud_x64.dll"
    cimgui_dynamic = f"{cimgui_build}/cimgui.dll"
elif "Linux" in operating_system:
    make_flavor = "Unix"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/linux/genie"
    genie_name = "genie"
    soloud_dynamic = f"{soloud_dir}/lib/libsoloud_x64.so"
    cimgui_dynamic = f"{cimgui_build}/cimgui.so"
else:
    raise UnsupportedPlatform

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

def task_soloud_dynamic():
    if "Windows" in operating_system:
        soloud_url = "http://sol.gfxile.net/soloud/soloud_20200207_lite.zip"
        dl_cmd = f"wget {soloud_url} -O {soloud_dir}/lite.zip"
        copy_cmd = f"unzip -j bin/soloud_x64.dll {soloud_dynamic}"
        return {
            'actions': [dl_cmd, copy_cmd],
            'targets': [soloud_dynamic],
            'file_dep': [genie_path, module_dep("cimgui")]
        }
    elif "Linux" in operating_system:
        genie_path = f"./3rd-party/{genie_name}"
        build_dir = f"{soloud_dir}/build"
        backends = "--with-portaudio --with-nosound"
        genie_cmd = f"{genie_path} --file={build_dir}/genie.lua {backends} --platform=x64 gmake"
        make_cmd = f"make -C {build_dir}/gmake config=release64 SoloudDynamic"
        return {
            'actions': [genie_cmd, make_cmd],
            'targets': [soloud_dynamic],
            'file_dep': [genie_path, module_dep("cimgui")]
        }
    else:
        raise UnsupportedPlatform

def task_soloud():
    genie_path = f"./3rd-party/{genie_name}"
    build_dir = f"{soloud_dir}/build"
    backends = "--with-portaudio --with-nosound"
    genie_cmd = f"{genie_path} --file={build_dir}/genie.lua {backends} --platform=x64 gmake"
    make_cmd = f"make -C {build_dir}/gmake config=release64 SoloudStatic"
    return {
        'actions': [genie_cmd, make_cmd],
        'targets': [soloud_static],
        'file_dep': [soloud_dynamic, module_dep("soloud")]
    }

def task_cimgui():
    cmd_static = f"cd {cimgui_build}; cmake .. -G '{make_flavor} Makefiles' -DIMGUI_STATIC=on"
    cmd_dynamic = f"cd {cimgui_build}; cmake .. -G '{make_flavor} Makefiles' -DIMGUI_STATIC=off"
    cmd_make = f"make -C {cimgui_build}"
    return {
        'actions': [f"mkdir -p {cimgui_build}", cmd_static, cmd_make, cmd_dynamic, cmd_make],
        'targets': [cimgui_static, cimgui_dynamic],
        'file_dep': [module_dep("cimgui")]
    }
