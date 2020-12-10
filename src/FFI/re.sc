using import ..radlib.core-extensions
using import ..radlib.foreign

define-scope re
    let header =
        include "../../3rd-party/tiny-regex-c/re.h"
    using header.extern
    using header.struct
    using header.typedef
    using header.enum
    using header.union
    using header.define
    using header.const

fn match? (pattern str)
    local size : i32
    let idx = (re.re_matchp pattern str &size)
    _ (idx != -1) idx (idx + size)

..
    sanitize-scope re "^re_"
    do
        let match?
        locals;
