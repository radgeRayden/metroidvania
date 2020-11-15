using import radlib.core-extensions
using import radlib.foreign

define-scope cjson
    let header =
        include "../../3rd-party/cJSON/cJSON.h"
    using header.extern
    using header.struct
    using header.typedef
    using header.enum
    using header.union
    using header.define
    using header.const

sanitize-scope cjson "^cJSON_"
