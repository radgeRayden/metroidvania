using import struct
using import glm
using import Array

import .filesystem

struct Sprite plain
    position : vec2
    scale : vec2 = (vec2 1)
    pivot : vec2
    rotation : f32
    texcoords : vec4 = (vec4 0 0 1 1)
    page : u32

struct ImageData
    data : (Array u8)
    width : usize
    height : usize
    channels : u32

do
    let
        Sprite
        ImageData
    locals;
