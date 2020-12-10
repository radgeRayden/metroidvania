# we redefine assert to avoid depending on the scopes runtime.
spice aot-assert (args...)
    let C = (import .radlib.libc)
    let printf = C.stdio.printf
    let abort = C.stdlib.abort

    inline check-assertion (result anchor msg)
        if (not result)
            printf "%s assertion failed: %s \n"
                anchor as rawstring
                msg
            abort;

    let argc = ('argcount args)
    verify-count argc 2 2
    let expr msg =
        'getarg args 0
        'getarg args 1

    let msgT = ('typeof msg)
    if ((msgT != string) and (msgT != rawstring))
        error "string expected as second argument"
    let anchor = ('anchor args)
    let anchor-text = (repr anchor)
    'tag `(check-assertion expr [anchor-text] (msg as rawstring)) anchor

spice gen-cenum-cstr (value)
    let T = ('typeof value)
    let sw = (sc_switch_new value)
    sc_switch_append_default sw `("?invalid?" as rawstring)
    for k v in ('symbols T)
        if (('typeof v) == T)
            let name = (tostring k)
            sc_switch_append_case sw v `(name as rawstring)
    sw

spice has-symbol? (T sym)
    """"Checks for the existence of a symbol in a type at compile time.
    T as:= type
    sym as:= Symbol
    try
        let sym = ('@ T sym)
        `true
    else
        `false

run-stage;

inline aot-assert (cond message)
    let message =
        static-if (none? message)
            ""
        else
            message
    aot-assert cond message

inline tocstr (v)
    static-if (not (has-symbol? (typeof v) '__tocstr))
        (tostring v) as rawstring
    else
        '__tocstr v

typedef+ CEnum
    inline __tocstr (v)
        gen-cenum-cstr v

typedef+ bool
    inline __tocstr (v)
        ? v ("true" as rawstring) ("false" as rawstring)

using import .constants
static-if AOT_MODE?
    set-globals!
        ..
            do
                let assert = aot-assert
                let tocstr
                locals;
            (globals)
    # we purge the package cache so our new global namespace is recognized.
    'set-symbol package 'modules (Scope)
else
    set-globals!
        ..
            do
                let tocstr
                locals;
            (globals)


static-if (not BUILD_MODE_AMALGAMATED?)
    switch operating-system
    case 'linux
        load-library "../lib/libgame.so"
        load-library "../lib/libglfw.so"
        load-library "../lib/libphysfs.so"
        load-library "../lib/cimgui.so"
        load-library "../lib/libsoloud_x64.so"
    case 'windows
        load-library "../lib/libgame.dll"
        load-library "../lib/glfw3.dll"
        load-library "../lib/libphysfs.dll"
        load-library "../lib/cimgui.dll"
        load-library "../lib/soloud_x64.dll"
    default
        error "Unsupported OS."

run-stage;

using import .main
static-if AOT_MODE?
    compile-object
        default-target-triple
        compiler-file-kind-object
        module-dir .. "/game.o"
        do
            let main = (static-typify main i32 (mutable@ rawstring))
            locals;
    try
        main (launch-args)
    else
        ;
else
    main (launch-args)
