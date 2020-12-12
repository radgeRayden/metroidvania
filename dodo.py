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
soloud_backends = ""

cimgui_dir = "./3rd-party/cimgui"
cimgui_static = f"{cimgui_dir}/cimgui.a"

operating_system = platform.system()
is_windows = operating_system.startswith("MINGW")
if is_windows:
    make_flavor = "MinGW"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/windows/genie.exe"
    genie_name = "genie.exe"
    soloud_dynamic = f"{soloud_dir}/lib/soloud_x64.dll"
    cimgui_dynamic = f"{cimgui_dir}/cimgui.dll"

    soloud_backends = "--with-miniaudio --with-wasapi"
elif "Linux" in operating_system:
    make_flavor = "Unix"
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/linux/genie"
    genie_name = "genie"
    soloud_dynamic = f"{soloud_dir}/lib/libsoloud_x64.so"
    cimgui_dynamic = f"{cimgui_dir}/cimgui.so"

    soloud_backends = "--with-portaudio"
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
    make_cmd = f"make -C {build_dir}/gmake config=release64 SoloudStatic"
    return {
        'actions': [f"rm -rf {build_dir}/gmake", genie_cmd, make_cmd],
        'targets': [soloud_static],
        'file_dep': [genie_path, soloud_dynamic, module_dep("soloud")]
    }

def task_cimgui():
    cmd_static = f"make -C {cimgui_dir} static"
    cmd_dynamic = f"make -C {cimgui_dir}"
    return {
        'actions': [cmd_static, cmd_dynamic],
        'targets': [cimgui_static, cimgui_dynamic],
        'file_dep': [module_dep("cimgui")]
    }
