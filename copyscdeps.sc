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

let libs... =
    "Array"
    "Box"
    "Capture"
    "chaining"
    "console"
    "core"
    "enum"
    "FunctionChain"
    "glm"
    "glsl"
    "itertools"
    "Map"
    "Option"
    "property"
    "Rc"
    "Set"
    "spicetools"
    "String"
    "struct"
    "testing"
    "UTF-8"

C.stdlib.system
    f"cp ${compiler-dir}/bin/${libname} ./bin/${libname}"
va-map
    inline (lib)
        C.stdlib.system
            f"cp -r ${compiler-dir}/lib/scopes/${lib}.sc ./lib/scopes/"
    libs...
C.stdlib.system
    f"cp -r ${compiler-dir}/lib/clang ./lib/clang"
