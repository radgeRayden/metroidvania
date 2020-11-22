using import ..radlib.core-extensions
using import ..radlib.foreign

define-scope c2
    let header =
        include "../../3rd-party/cute_headers/cute_c2.h"
    using header.extern
    using header.struct
    using header.typedef
    using header.enum
    using header.union
    using header.define
    using header.const

sanitize-scope c2 "^c2"
