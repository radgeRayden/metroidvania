using import .radlib.core-extensions

using import struct
using import Array
using import glm
using import Rc

let c2 = (import .FFI.c2)

semantically-bind-types c2.v vec2
    inline "conv-to" (self)
        vec2 self.x self.y
    inline "conv-from" (other)
        c2.v (unpack other)


# WORLD INTERNAL STATE
# ================================================================================
struct Collider
global objects : (Array (Rc Collider))

struct Collider
    aabb : c2.AABB

    fn try-move (self)
        for obj in objects
            if (obj != self)
                # ...

fn register-object (col)
    'append objects col

do
    let
        Collider

        register-object
    locals;
