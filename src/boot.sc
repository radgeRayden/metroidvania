# To avoid cluttering the package namespace, lets import all C functions
  we make use of at this stage.
vvv bind C
do
    let header =
        include
            """"void abort();
                int printf(const char *restrict format, ...);
                int system(const char *command);
                int chdir(const char *path);

    using header.extern
    unlet header
    locals;

# we redefine assert to avoid depending on the scopes runtime.
spice _aot-assert (args...)
    inline check-assertion (result anchor msg)
        if (not result)
            C.printf "%s assertion failed: %s \n"
                anchor as rawstring
                msg
            C.abort;

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

set-globals!
    ..
        do
            let assert = aot-assert
            let tocstr
            locals;
        (globals)
# we purge the package cache so our new global namespace is recognized.
'set-symbol package 'modules (Scope)
require-from module-dir ".runtime"
run-stage;

import .config
'set-symbol config 'AOT_MODE? true

let argc argv = (launch-args)
let silent? = ((argc > 2) and ((string (argv @ 2)) == "-silent"))
run-stage;

inline filter-argv ()
    _ argc argv

using import .main
report "system" ("mkdir -p " .. module-dir .. "/../build")
C.system ("mkdir -p " .. module-dir .. "/../build")
compile-object
    default-target-triple
    compiler-file-kind-object
    module-dir .. "/../build/game.o"
    do
        let main = (static-typify main i32 (mutable@ rawstring))
        locals;

static-if (not silent?)
    try
        report "chdir" module-dir
        C.chdir module-dir
        main (filter-argv)
    else
        ;
