using import enum
using import struct
using import glm
using import String
using import Map

enum EntityKind plain
    Player = 0
    inline __hash (self)
        hash (self as i32)

struct Entity plain
    position : vec2
    velocity : vec2
    grounded? : bool
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords
    tag : EntityKind

global archetypes : (Map EntityKind Entity)

fn init-archetypes ()
    'set archetypes EntityKind.Player
        Entity
            sprite = 23
            tag = EntityKind.Player
    locals;

do
    let EntityKind Entity archetypes init-archetypes
    locals;
