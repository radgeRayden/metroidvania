# TODO: at some point we'll want to get a proper semver string. Maybe gather it from
# a tag? Or some other file in the source directory.
let C = (import .src.radlib.libc)
let game-version =
    label git-log
        let version-size = 19
        let handle = (C.stdio.popen "git log -1 --format='v%cd.%h' --date=short 2>/dev/null" "r")
        if (handle == null)
            merge git-log ("UNKNOWN-nogit" as rawstring)
        local str : (array i8 version-size)
        C.stdio.fgets &str version-size handle
        if ((C.stdio.pclose handle) != 0)
            merge git-log ("UNKNOWN-nogit" as rawstring)
        (string (&str as (pointer i8)) version-size) as rawstring
run-stage;

fn dummyfn ()
    ;

fn GAME_VERSION ()
    game-version

fn main (argc argv)
    let sc_init = (extern 'sc_init (function void voidstar i32 (pointer rawstring)))
    let sc_main = (extern 'sc_main (function i32))

    sc_init (static-typify dummyfn) argc argv
    sc_main;

compile-object
    default-target-triple
    compiler-file-kind-object
    "game.o"
    do
        let
            main = (static-typify main i32 (pointer rawstring))
            GAME_VERSION = (static-typify GAME_VERSION rawstring)
        locals;

