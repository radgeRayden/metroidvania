using import Array

load-library "lib/libgame.so"
load-library "lib/libphysfs.so"
run-stage;

let cjson = (import .src.FFI.cjson)
let physfs = (import .src.FFI.physfs)
let stbi = (import .src.FFI.stbi)
using import .src.common

let argc argv = (launch-args)
if (not (physfs.init (argv @ 0)))
    error "Failed to initialize PHYSFS."
physfs.mount "./source-assets" "/" true
physfs.setWriteDir "./data"

local sheet =
    Struct.__typecall ImageData
        width = 1024
        height = 1024
        channels = 4
        data = ((Array u8))
'resize sheet.data (* sheet.width sheet.height sheet.channels)

fn paste-image (src dst dx dy)
    let sw sh = src.width src.height
    let dw dh = dst.width dst.height
    assert
        and
            sw <= dw
            sh <= dh
    if (or
        ((dx + sw) > dw)
        ((dy + sh) > dh))
        raise none

    using import itertools
    for x y in (dim sw sh)
        src-texel := y * sw * 4 + x * 4
        dst-texel := ((dy + y) * dw + dx + x) * 4
        va-map
            inline (offset)
                dst.data @ (dst-texel + offset) = (src.data @ (src-texel + offset))
            va-range 4
    print "success"
    ;

let base-path = "Roguelike Dungeon - Asset Bundle/Sprites/Monsters/Skeleton/Variant1"
let files =
    physfs.enumerateFiles base-path
loop (idx x y = 0 0:usize 0:usize)
    let cur = (files @ idx)
    if (cur == null)
        break;

    let img = (ImageData (base-path .. "/" .. (string cur)))
    try
        print x y
        paste-image img sheet x y
    else
        ;
   
    _ (idx + 1) (x + (copy img.width)) 0:usize

stbi.write_png "sheet.png" 1024 1024 4 sheet.data (1024 * 4)

physfs.freeList (files as voidstar)
