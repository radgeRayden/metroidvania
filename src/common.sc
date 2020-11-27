using import struct
using import glm
using import Array

import .filesystem

let C = (import .radlib.libc)
let stbi = (import .FFI.stbi)

let argc argv = (launch-args)
let BUILD_MODE_AMALGAMATED? = ((argc > 2) and ((string (argv @ 2)) == "-amalgamated"))
run-stage;

vvv bind constants
do
    let BUILD_MODE_AMALGAMATED?
    let INTERNAL_RESOLUTION = (ivec2 (1920 // 6) (1080 // 6))
    let ATLAS_PAGE_SIZE = (ivec2 1024 1024)
    let GAME_VERSION =
        static-if BUILD_MODE_AMALGAMATED?
            string (call (extern 'GAME_VERSION (function rawstring)))
        else
            do
                label git-log
                    let version-size = 19
                    let handle = (C.stdio.popen "git log -1 --format='v%cd.%h' --date=short 2>/dev/null" "r")
                    if (handle == null)
                        merge git-log "UNKNOWN-nogit"
                    local str : (array i8 version-size)
                    C.stdio.fgets &str version-size handle
                    if ((C.stdio.pclose handle) != 0)
                        merge git-log "UNKNOWN-nogit"
                    string (&str as (pointer i8)) version-size


    locals;

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
        constants
        Sprite
        ImageData
    locals;
