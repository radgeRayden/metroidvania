let C = (import radlib.libc)
using import radlib.stringtools
let libname =
    switch operating-system
    case 'linux
        "libscopesrt.so"
    case 'windows
        "scopesrt.dll"
    default
        error "Unsupported OS"
C.stdlib.system
    f"cp ${compiler-dir}/bin/${libname} ./bin/${libname}"
C.stdlib.system
    f"cp -r ${compiler-dir}/lib/scopes ./lib/scopes"
C.stdlib.system
    f"cp -r ${compiler-dir}/lib/clang ./lib/clang"
