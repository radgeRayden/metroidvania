let C = (import .radlib.libc)
# we redefine assert to avoid depending on the scopes runtime.
spice _aot-assert (args...)
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

sugar aot-assert (args...)
    let args = (args... as list)
    let cond msg body = (decons args 2)
    let anchor = ('anchor cond)
    let msg = (convert-assert-args args cond msg)
    list ('tag `_aot-assert anchor) cond msg

# FIXME: will break with duplicate enum tags
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

using import .config
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
    require-from module-dir ".runtime"

C.unistd.chdir module-dir
run-stage;

using import .main
static-if AOT_MODE?
    C.stdlib.system "mkdir -p ../build"
    compile-object
        default-target-triple
        compiler-file-kind-object
        module-dir .. "/../build/game.o"
        do
            let main = (static-typify main i32 (mutable@ rawstring))
            locals;
    try
        main (launch-args)
    else
        ;
else
    main (launch-args)
