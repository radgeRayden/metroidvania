import .config

let soloud = (import .FFI.soloud)
let C = (import .radlib.libc)
import .io

global soloud-instance : (mutable@ soloud.Soloud)

fn init ()
    raising Nothing
    soloud-instance = (soloud.create)

    inline try-backend (backend)
        let result =
            soloud.initEx
                soloud-instance
                soloud.SOLOUD_CLIP_ROUNDOFF
                backend
                soloud.SOLOUD_AUTO
                soloud.SOLOUD_AUTO
                2
        if result
            io.log (soloud.getErrorString soloud-instance result)
        result == 0

    static-match operating-system
    case 'linux
        if (try-backend soloud.SOLOUD_PORTAUDIO)
            return;
        else
            io.log "Portaudio backend not available. Consider installing the portaudio package for your distribution.\n"
            ;
        if (try-backend soloud.SOLOUD_MINIAUDIO)
            return;
        else
            io.log "Failed to initialize sound system.\n"
            ;
    case 'windows
        if (try-backend soloud.SOLOUD_MINIAUDIO)
            return;
        else
            io.log "Failed to initialize miniaudio backend.\n"
            ;
        if (try-backend soloud.SOLOUD_WASAPI)
            return;
        else
            io.log "Failed to initialize sound system.\n"
            ;
    default
        error "unsupported OS"

fn cleanup ()
    soloud.deinit soloud-instance
    soloud.destroy soloud-instance

do
    let
        soloud-instance
        init
        cleanup
    locals;
