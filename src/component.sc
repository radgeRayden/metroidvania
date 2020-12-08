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
using import .constants

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

        fn destroy (self parent)
            if self.trigger?
                collision.remove-trigger parent.id
            else
                collision.remove-object parent.id
            ;

    struct MessageBoxTrigger < ComponentBase
        msg-index : u32

        fn on-trigger-enter (self parent other)
            let Tag = (typeof other.tag)
            if (other.tag == Tag.Player)
                show-msgbox = true
            parent.alive? = false

        fn on-trigger-exit (self parent other)
            let Tag = (typeof other.tag)
            if (other.tag == Tag.Player)
                show-msgbox = false

    struct CoinBehaviour < ComponentBase
        value : u32

        fn on-trigger-enter (self parent other)
            let soloud = (import .FFI.soloud)
            import .sound
            let sfxr = (soloud.Sfxr_create)
            soloud.Sfxr_loadPreset sfxr soloud.SFXR_COIN 1000
            soloud.play sound.soloud-instance sfxr

            parent.alive? = false
            ;

        fn on-trigger-exit (self parent other)
            ;

    # mockup of enemy AI
    struct DuckyBehaviour < ComponentBase
        hp : i32
        grounded? : bool
        velocity : vec2
        _collider : (Rc collision.Collider)

        fn init (self parent)
            for component in parent.components
                dispatch component
                case Hitbox (hitbox)
                    self._collider = (copy hitbox.collider)
                    break;
                default
                    ;

        fn update (self parent dt)
            # copied from boot.update
            let yvel = self.velocity.y
            let xvel = self.velocity.x
            # apply gravity
            if ((deref self.grounded?) and (yvel <= 0))
                yvel = -1
            else
                yvel = (clamp (yvel + (GRAVITY * dt)) -100. 200.)

            # NOTE: perhaps it is confusing to have the position in the entity.
            let pos = (parent.position + self.velocity * dt)

            # copied from player-move
            let col = ('try-move self._collider pos)
            try
                let col = ('unwrap col)
                if (col.normal.y < 0)
                    self.grounded? = true
                elseif (col.normal.y > 0)
                    self.velocity.y = 0
                elseif (col.normal != 0)
                    self.velocity.x = 0
            else
                self.grounded? = false
                ;
            parent.position = self._collider.Position
            ;

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
