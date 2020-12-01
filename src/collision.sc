using import .radlib.core-extensions

using import struct
using import Array
using import glm
using import Rc
using import property

let c2 = (import .FFI.c2)

semantically-bind-types c2.v vec2
    inline "conv-to" (self)
        vec2 self.x self.y
    inline "conv-from" (other)
        c2.v (unpack other)

typedef+ c2.AABB
    fn project (self position)
        let size = (self.max - self.min)
        this-type
            min = position
            max = (position + size)

# WORLD INTERNAL STATE
# ================================================================================
struct Collider
global objects : (Array (Rc Collider))

struct Collider
    id : usize
    aabb : c2.AABB

    let Position =
        property
            inline "get" (self)
                imply self.aabb.min vec2
            inline "set" (self value)
                self.aabb = ('project self.aabb value)
                ;

    fn try-move (self pos)
        local collided? : bool
        for obj in objects
            if (obj.id == self.id)
                continue;
            local manifold : c2.Manifold
            c2.AABBtoAABBManifold
                'project self.aabb pos
                obj.aabb
                &manifold
            if (manifold.count > 0)
                collided? = true
                let normal = (imply manifold.n vec2)
                let depth = (manifold.depths @ 0)
                self.Position = (pos - (normal * depth))

        if (not collided?)
            self.Position = pos

    global gid-counter : usize
    inline __typecall (cls)
        let new-id = (deref gid-counter)
        gid-counter += 1
        super-type.__typecall cls
            id = new-id

fn register-object (col)
    'append objects col

do
    let
        Collider

        objects

        register-object
    locals;
