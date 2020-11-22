fn dummyfn ()
    ;

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
        locals;

