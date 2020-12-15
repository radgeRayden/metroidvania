using import ..radlib.foreign

vvv bind stbsp
do
    let header =
        include
            options (.. "-I" module-dir "/../../3rd-party/stb")
            "stb_sprintf.h"
    using header.extern filter "^stbsp_"
    unlet header
    locals;

sanitize-scope stbsp "^stbsp_"
