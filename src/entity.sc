using import .radlib.core-extensions

using import enum
using import struct
using import glm
using import String
using import Map
using import Array

struct SpriteComponent
struct EntityId plain
    _idx : usize
    _gid : usize
    inline __== (selfT otherT)
        static-if (selfT == otherT)
            inline (self other)
                and
                    self._idx == other._idx
                    self._gid == other._gid

    position : vec2
    texcoords : vec4
    page : u32

enum Component
    Sprite : SpriteComponent
    let __typecall = enum-class-constructor

enum EntityKind plain
    Player = 0
    inline __hash (self)
        hash (self as i32)

struct Entity
    id : EntityId
    position : vec2
    velocity : vec2
    grounded? : bool
    components : (Array Component)
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords
    tag : EntityKind = EntityKind.Player

let EntityConstructor = (@ (function (uniqueof Entity -1)))
global archetypes : (Map EntityKind EntityConstructor)

inline set-archetype (tag f)
    'set archetypes tag (static-typify f)

let ComponentList = (Array Component)
typedef+ ComponentList
    inline __typecall (cls ...)
        local arr = (super-type.__typecall cls)
        va-map
            inline (c)
                'emplace-append arr
                    c
            ...
        deref arr

enum EntityError plain
    StaleReference

struct EntityList
    _next-vacant : usize = 0
    _entities : (Array Entity)
    inline __as (selfT otherT)
        static-if (otherT == Generator)
            inline (self)
                self._entities as Generator

    fn get (self id)
        let ent = (self._entities @ id._idx)
        if (ent.id != id)
            raise EntityError.StaleReference
        view ent

    fn add (self ent)
        let new-index = self._next-vacant
        global gid : usize 0
        if (new-index == 0)
            'append self._entities ent
        else
            self._entities @ new-index = ent

        let ent = (self._entities @ new-index)
        ent.id =
            EntityId
                _idx = self._next-vacant
                _gid = gid

        gid += 1
        self._next-vacant += 1
        view ent

    fn remove (self id)
        # make sure the id is valid
        'get self id
        let last-index = ((countof self._entities) - 1)
        'swap self._entities id.idx last-index
        'remove self._entities last-index

fn init-archetypes ()
    set-archetype EntityKind.Player
        fn ()
            Entity
                sprite = 23
                tag = EntityKind.Player
                components =
                    ComponentList
                        SpriteComponent (page = 23)
    locals;

do
    let EntityKind EntityId Entity EntityList EntityError archetypes init-archetypes
    locals;
