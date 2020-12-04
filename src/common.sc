using import struct
using import glm
using import Array

import .filesystem

let stbi = (import .FFI.stbi)

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

    fn load-image-data (filename)
        let data = (filesystem.load-full-file filename)
        local x : i32
        local y : i32
        local n : i32
        let img-data =
            stbi.load_from_memory
                (imply data pointer) as (pointer u8)
                (countof data) as i32
                &x
                &y
                &n
                0
        let data-len = (x * y * n)
        _
            Struct.__typecall (Array u8)
                _items = img-data
                _count = data-len
                _capacity = data-len
            deref x
            deref y
            deref n

    inline __typecall (cls filename)
        let data w h c = (load-image-data filename)
        super-type.__typecall cls
            data = data
            width = (w as usize)
            height = (h as usize)
            channels = (c as u32)

do
    let
        Sprite
        ImageData
    locals;
