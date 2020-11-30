using import enum
using import struct
using import Array

sugar enum-from-scope (name scope-name)
    name as:= Symbol
    let scope = ((sc_prove (sc_expand scope-name '() sugar-scope) ()) as Scope)
    let tags =
        loop (index tags = -1 '())
            let key value index =
                sc_scope_next scope index
            if (index < 0)
                break ('reverse tags)
            _ index
                cons
                    qq
                        [key] : [value]
                    tags
    qq
        enum [name]
            unquote-splice tags
run-stage;

using import .radlib.core-extensions
import .common
import .renderer

typedef ComponentBase < Struct
    fn update (...)
        ;
    fn draw (...)
        ;

# COMPONENT DEFINITIONS
# ================================================================================
vvv bind components
do
    struct Sprite < ComponentBase
        layer : u32
        sprite : common.Sprite

        fn update (self dt parent)
            self.sprite.position = (floor parent.position)
            ;

        fn draw (self parent)
            'add (renderer.sprite-layers @ self.layer) self.sprite
            ;

    locals;

# I need this for some reason
run-stage;

# INTERFACE
# ================================================================================
enum-from-scope Component components
typedef+ Component
    let __typecall = enum-class-constructor

    inline update (self dt parent)
        'apply self
            (T self) -> ('update self dt parent)

    inline draw (self parent)
        'apply self
            (T self) -> ('draw self parent)

let ComponentList = (Array Component)
typedef+ ComponentList
    inline __typecall (cls ...)
        local arr = (super-type.__typecall cls)
        va-map
            inline (c)
                'emplace-append arr c
            ...
        deref arr

do
    let
        Component
        ComponentList
        components
    locals;
