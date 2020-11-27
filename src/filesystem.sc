using import Array
using import String

let physfs = (import .FFI.physfs)
let C = (import .radlib.libc)

fn init (argv)
    if (not (physfs.init (argv @ 0)))
        error "Failed to initialize PHYSFS."
    physfs.mount "../data" "/" true
    physfs.setWriteDir "."

fn load-full-file (filename)
    let file = (physfs.openRead filename)
    if (file == null)
        hide-traceback;
        error (.. "could not open file " (string filename))

    let size = (physfs.fileLength file)
    local data : (Array i8)
    'resize data size
    let read = (physfs.readBytes file data (size as u64))
    assert (read == size)

    data

fn get-directory-files (path)
    let flist = (physfs.enumerateFiles path)

    local file-array : (Array String)
    loop (idx = 0:usize)
        let current = (flist @ idx)
        if (current == null)
            break;

        'append file-array (String current (C.string.strlen current))
        idx + 1

    physfs.freeList (flist as voidstar)
    file-array

do
    let load-full-file
        init
        get-directory-files
    locals;
