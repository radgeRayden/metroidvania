import platform
class UnsupportedPlatform(Exception):
    pass

genie_url = ""
genie_name = ""

soloud_static = "./3rd-party/soloud/lib/libsoloud_static_x64.a"
soloud_dynamic = ""

operating_system = platform.system()
if "Windows" in operating_system:
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/windows/genie.exe"
    genie_name = "genie.exe"
    soloud_dynamic = "./3rd-party/soloud/lib/soloud_x64.dll"
elif "Linux" in operating_system:
    genie_url = "https://github.com/bkaradzic/bx/raw/master/tools/bin/linux/genie"
    genie_name = "genie"
    soloud_dynamic = "./3rd-party/soloud/lib/libsoloud_x64.so"
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

def task_soloud():
    genie_path = f"./3rd-party/{genie_name}"
    build_dir = "./3rd-party/soloud/build"
    backends = "--with-portaudio --with-nosound"
    genie_cmd = f"{genie_path} --file={build_dir}/genie.lua {backends} --platform=x64 gmake"
    make_cmd = f"make -C {build_dir}/gmake config=release64"
    return {
        'actions': [genie_cmd, make_cmd],
        'targets': [soloud_static, soloud_dynamic],
        'file_dep': [genie_path]
    }
