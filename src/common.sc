using import struct
using import glm
using import Array
using import enum

import .filesystem

struct Sprite plain
    position : vec2
    scale : vec2 = (vec2 1)
    pivot : vec2
    rotation : f32
    texcoords : vec4 = (vec4 0 0 1 1)
    page : u32

struct Vertex2D plain
    position : vec2
    color : vec4
    texcoords : vec3

struct ImageData
    data : (Array u8)
    width : usize
    height : usize
    channels : u32

enum EntityKind plain
    Player = 0
    Ducky = 1
    Skeleton = 2
    Tilemap = 3
    Coin = 4

    inline __hash (self)
        hash (self as i32)

do
    let
        Sprite
        Vertex2D
        ImageData
        EntityKind
    locals;
