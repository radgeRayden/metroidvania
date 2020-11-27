using import ..radlib.foreign
using import ..radlib.core-extensions
define-scope stbi
    let header =
        include
            options (.. "-I" module-dir "/../../3rd-party/stb")
            "stb_image.h"

    using header.extern filter "^stbi_"

define-scope stbiw
    let header =
        include
            options (.. "-I" module-dir "/../../3rd-party/stb")
            "stb_image_write.h"

    using header.extern filter "^stbi_"

sanitize-scope (stbi .. stbiw) "^stbi_"
