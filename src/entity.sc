using import enum
using import struct
using import glm
using import String

enum Archetype plain
    Player = 0

struct Entity plain
    position : vec2
    velocity : vec2
    grounded? : bool
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords
    tag : Archetype

vvv bind archetypes
do
    let player =
        Entity
            sprite = 23
            tag = Archetype.Player
    locals;

do
    let Archetype Entity archetypes
    locals;
