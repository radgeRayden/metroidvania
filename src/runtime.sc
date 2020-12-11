switch operating-system
case 'linux
    load-library (.. module-dir "/../lib/libgame.so")
    load-library (.. module-dir "/../lib/libglfw.so")
    load-library (.. module-dir "/../lib/libphysfs.so")
    load-library (.. module-dir "/../lib/cimgui.so")
    load-library (.. module-dir "/../lib/libsoloud_x64.so")
case 'windows
    load-library (.. module-dir "/../lib/libgame.dll")
    load-library (.. module-dir "/../lib/glfw3.dll")
    load-library (.. module-dir "/../lib/libphysfs.dll")
    load-library (.. module-dir "/../lib/cimgui.dll")
    load-library (.. module-dir "/../lib/soloud_x64.dll")
default
    error "Unsupported OS."
