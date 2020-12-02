using import .radlib.core-extensions

using import struct
using import Array
using import glm
using import Rc
using import property
using import Option

let c2 = (import .FFI.c2)
import .math

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

struct Collision plain
    normal : vec2
    contact : vec2

struct Collider
global objects : (Array (Rc Collider))

struct LevelCollisionInfo
    matrix : (Array bool)
    level-size : vec2
    tile-size : vec2

global level-info : LevelCollisionInfo

fn resolve-object<->map (obj new-pos)
    local last-collision : (Option Collision)
    let aabb = obj.aabb
    # scene is all on positive atm, so just do whatever. Could also
    # block, but I think it might be useful to not do that (eg. to transition rooms)
    if (or
        (new-pos.x < 0)
        (new-pos.y < 0)
        (new-pos.x > level-info.level-size.x)
        (new-pos.y > level-info.level-size.y))
        obj.Position = new-pos
        return last-collision

    let tile-size = level-info.tile-size
    level-size-tiles := level-info.level-size / tile-size

    # go through all tiles that potentially intersect our object
    let region-min = (floor (aabb.min / tile-size))
    let region-max = (math.ceil (aabb.max / tile-size))
    region-size := region-max - region-min

    let positive-size? = ((region-size.x > 0) and (region-size.y > 0))
    assert positive-size? (.. "invalid level info " (repr region-min) (repr region-max))

    using import itertools
    local collided? : bool
    fold (new-pos = new-pos) for ox oy in (dim (region-size.x as u32) (region-size.y as u32))
        tile := region-min + (vec2 ox oy)

        # sample tilemap
        let solid? =
            do
                let inw inh = (unpack (tile < level-size-tiles))
                if (and inw inh)
                    idx := (level-size-tiles.y - 1 - tile.y) * level-size-tiles.x + tile.x
                    deref
                        level-info.matrix @ (idx as usize)
                else
                    false

        if (not solid?)
            repeat new-pos

        local manifold : c2.Manifold
        c2.AABBtoAABBManifold
            'project aabb new-pos
            c2.AABB
                tile * tile-size
                (tile + 1) * tile-size
            &manifold

        if (manifold.count > 0)
            last-collision =
                Collision
                    manifold.n
                    manifold.contact_points @ 0
            collided? = true
            let normal = (imply manifold.n vec2)
            let depth = (manifold.depths @ 0)
            let pvec = (normal * depth)

            pos := new-pos - pvec
            obj.Position = pos

            repeat pos
        new-pos

    if (not collided?)
        obj.Position = new-pos

    deref last-collision

fn resolve-object<->objects (moving new-pos)
    local collided? : bool
    local last-collision : (Option Collision)
    fold (new-pos = new-pos) for obj in objects
        if (obj.id == moving.id)
            repeat new-pos
        local manifold : c2.Manifold
        c2.AABBtoAABBManifold
            'project moving.aabb new-pos
            obj.aabb
            &manifold
        if (manifold.count > 0)
            last-collision =
                Collision
                    manifold.n
                    manifold.contact_points @ 0
            collided? = true
            let normal = (imply manifold.n vec2)
            let depth = (manifold.depths @ 0)
            let pos = (new-pos - (normal * depth))
            moving.Position = pos
            repeat pos
        new-pos

    if (not collided?)
        moving.Position = new-pos

    deref last-collision

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
        """"Moves the Collider while resolving collisions with terrain and other colliders.
            Returns the normal and the contact point of the last collision, if any.
        # let map-collision = (resolve-object<->map self pos)
        # let object-collision = (resolve-object<->objects self (imply self.Position vec2))
        let object-collision = (resolve-object<->objects self pos)
        # map-collision or object-collision

    global gid-counter : usize
    inline __typecall (cls)
        let new-id = (deref gid-counter)
        gid-counter += 1
        super-type.__typecall cls
            id = new-id

fn configure-level (collision-info)
    level-info = collision-info

fn register-object (col)
    'append objects col

do
    let
        Collider
        LevelCollisionInfo

        objects

        register-object
        configure-level
    locals;
