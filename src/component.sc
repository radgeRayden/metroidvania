using import enum
using import struct
using import Array
using import Rc
using import glm

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
import .collision
import .event-system

global show-msgbox : bool

typedef ComponentBase < Struct
    fn init (...)
        ;
    fn update (...)
        ;
    fn draw (...)
        ;
    fn destroy (...)
        ;

# COMPONENT DEFINITIONS
# ================================================================================
vvv bind components
do
    struct Sprite < ComponentBase
        layer : u32
        sprite : common.Sprite

        fn update (self parent dt)
            self.sprite.position = (floor parent.position)
            ;

        fn draw (self parent)
            'add (renderer.sprite-layers @ self.layer) self.sprite
            ;

    struct Hitbox < ComponentBase
        offset : vec2
        size : vec2
        collider : (Rc collision.Collider)
        trigger? : bool

        fn init (self parent)
            aabb-min := parent.position + self.offset
            aabb-max := aabb-min + self.size
            self.collider.id = parent.id
            self.collider.aabb =
                typeinit
                    aabb-min
                    aabb-max

            if (not self.trigger?)
                collision.register-object (copy self.collider)
            else
                collision.register-trigger (copy self.collider)
            ;
            ;

    struct MessageBoxTrigger < ComponentBase
        msg-index : u32

        fn on-trigger-enter (self parent other)
            let Tag = (typeof other.tag)
            if (other.tag == Tag.Player)
                show-msgbox = true

        fn on-trigger-exit (self parent other)
            let Tag = (typeof other.tag)
            if (other.tag == Tag.Player)
                show-msgbox = false

    locals;

# I need this for some reason
run-stage;

# INTERFACE
# ================================================================================
enum-from-scope Component components
typedef+ Component
    let __typecall = enum-class-constructor

    inline init (self parent)
        'apply self
            (T self) -> ('init self parent)

    inline update (self parent dt)
        'apply self
            (T self) -> ('update self parent dt)

    inline draw (self parent)
        'apply self
            (T self) -> ('draw self parent)

    inline destroy (self parent)
        'apply self
            (T self) -> ('destroy self parent)


let ComponentList = (Array (Rc Component))
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

        show-msgbox
    locals;
