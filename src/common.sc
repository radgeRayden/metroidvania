using import struct
using import glm

struct Sprite plain
    position : vec2
    scale : vec2 = (vec2 1)
    pivot : vec2
    rotation : f32
    texcoords : vec4 = (vec4 0 0 1 1)
    page : u32

do
    let Sprite
    locals;
