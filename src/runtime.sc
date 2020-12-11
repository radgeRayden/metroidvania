switch operating-system
case 'linux
    load-library "../lib/libgame.so"
    load-library "../lib/libglfw.so"
    load-library "../lib/libphysfs.so"
    load-library "../lib/cimgui.so"
    load-library "../lib/libsoloud_x64.so"
case 'windows
    load-library "../lib/libgame.dll"
    load-library "../lib/glfw3.dll"
    load-library "../lib/libphysfs.dll"
    load-library "../lib/cimgui.dll"
    load-library "../lib/soloud_x64.dll"
default
    error "Unsupported OS."
