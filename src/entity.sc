using import .radlib.core-extensions

using import enum
using import struct
using import glm
using import String
using import Map
using import Array

struct SpriteComponent
    position : vec2
    texcoords : vec4
    page : u32

enum Component
    Sprite : SpriteComponent
    let __typecall = enum-class-constructor

enum EntityKind plain
    Player = 0
    inline __hash (self)
        hash (self as i32)

struct Entity
    id : u32
    position : vec2
    velocity : vec2
    grounded? : bool
    components : (Array Component)
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords
    tag : EntityKind = EntityKind.Player

global archetypes : (Map EntityKind (@ (function (uniqueof Entity -1))))

inline set-archetype (tag f)
    'set archetypes tag (static-typify f)

let ComponentList = (Array Component)
typedef+ ComponentList
    inline __typecall (cls ...)
        local arr = (super-type.__typecall cls)
        va-map
            inline (c)
                'emplace-append arr
                    c
            ...
        deref arr

fn init-archetypes ()
    set-archetype EntityKind.Player
        fn ()
            Entity
                sprite = 23
                tag = EntityKind.Player
                components =
                    ComponentList
                        SpriteComponent (page = 23)
    locals;

do
    let EntityKind Entity archetypes init-archetypes
    locals;
