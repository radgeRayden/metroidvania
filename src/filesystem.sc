using import Array

let physfs = (import .FFI.physfs)

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


do
    let load-full-file init
    locals;
