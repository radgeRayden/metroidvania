using import enum
using import struct
using import glm
using import String
using import Map

enum EntityKind plain
    Player = 0
    inline __hash (self)
        hash (self as i32)

struct Entity
    id : u32
    position : vec2
    velocity : vec2
    grounded? : bool
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords
    tag : EntityKind = EntityKind.Player

global archetypes : (Map EntityKind (@ (function (uniqueof Entity -1))))

inline set-archetype (tag f)
    'set archetypes tag (static-typify f)
fn init-archetypes ()
    set-archetype EntityKind.Player
        fn ()
            Entity
                sprite = 23
                tag = EntityKind.Player
    locals;

do
    let EntityKind Entity archetypes init-archetypes
    locals;
