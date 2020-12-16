let C = (import .radlib.libc)
using import String

spice constant-cstr-len (str)
    str as:= rawstring
    spice-quote [((C.string.strlen str) as usize)]

run-stage;

inline instrument-stringT (T)
    typedef+ T
        inline equals? (self other)
            (C.string.strcmp self other) == 0

        inline __countof (self)
            static-if (constant? self)
                constant-cstr-len self
            else
                (C.string.strlen self) as usize
instrument-stringT rawstring
instrument-stringT (mutable rawstring)

inline prefix:cs (literal)
    literal as rawstring

inline prefix:s (literal)
    String literal

do
    let prefix:cs prefix:s
    locals;
