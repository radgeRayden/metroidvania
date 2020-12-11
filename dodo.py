import platform
class UnsupportedPlatform(Exception):
    pass

operating_system = platform.system()

from doit.tools import LongRunning
def task_launch():
    """launch the game"""
    cmd = "scopes ./src/boot.sc"
    return {
            'actions': [LongRunning(cmd)]
        }
