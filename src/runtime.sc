switch operating-system
case 'linux
    load-library (.. module-dir "/../build/libgame.so")
    load-library (.. module-dir "/../build/libglfw.so")
    load-library (.. module-dir "/../build/libphysfs.so")
    load-library (.. module-dir "/../build/cimgui.so")
    load-library (.. module-dir "/../build/libsoloud_x64.so")
case 'windows
    load-library (.. module-dir "/../build/libgame.dll")
    load-library (.. module-dir "/../build/soloud_x64.dll")
default
    error "Unsupported OS."
