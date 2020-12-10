let soloud = (import .FFI.soloud)
let C = (import .radlib.libc)

global soloud-instance : (mutable@ soloud.Soloud)
fn init ()
    soloud-instance = (soloud.create)
    let backend =
        static-match operating-system
        case 'linux
            soloud.SOLOUD_PORTAUDIO
        case 'windows 
            soloud.SOLOUD_MINIAUDIO
        default
            error "unsupported OS"

    let result = 
        soloud.initEx
            soloud-instance
            soloud.SOLOUD_CLIP_ROUNDOFF
            backend
            soloud.SOLOUD_AUTO
            soloud.SOLOUD_AUTO
            2
    if result
        C.stdio.puts "SOLOUD ERROR:"
        assert false (soloud.getErrorString soloud-instance result)

fn cleanup ()
    soloud.deinit soloud-instance
    soloud.destroy soloud-instance

do
    let
        soloud-instance
        init
        cleanup
    locals;
