using import glm

let C = (import .radlib.libc)

let argc argv = (launch-args)
let BUILD_MODE_AMALGAMATED? = ((argc > 2) and ((string (argv @ 2)) == "-amalgamated"))
run-stage;

do
    # CONFIGURATION
    # ================================================================================
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

    # GAMEPLAY VALUES
    # ================================================================================
    let GRAVITY = -240

    locals;
