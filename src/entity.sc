using import .radlib.core-extensions

using import enum
using import struct
using import glm
using import String
using import Map
using import Array
using import Rc

let EntityId = u32

typedef ComponentBase < Struct
    fn update (...)
        ;
    fn draw (...)
        ;

struct SpriteComponent < ComponentBase
    position : vec2
    texcoords : vec4
    page : u32

    fn update (self parent)
        self.position = (floor parent.position)
        ;

enum Component
    Sprite : SpriteComponent
    let __typecall = enum-class-constructor
    inline update (self parent)
        'apply self
            (T self) -> ('update self parent)

enum EntityKind plain
    Player = 0
    inline __hash (self)
        hash (self as i32)

struct Entity
    id : EntityId
    tag : EntityKind = EntityKind.Player
    # if false, can be removed at any moment
    alive? : bool = true
    position : vec2
    velocity : vec2
    grounded? : bool
    components : (Array Component)
    sprite : u32 # FIXME: shouldn't be here, and moreover should include texcoords


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
    _entities : (Array (Rc Entity))
    _entity-lookup : (Map EntityId (Rc Entity))

    inline __as (selfT otherT)
        static-if (otherT == Generator)
            inline (self)
                self._entities as Generator

    inline get (self id)
        'get self._entity-lookup id

    global gid : EntityId
    fn add (self ent)
        let ent = ('append self._entities (Rc.wrap ent))
        ent.id = gid
        'set self._entity-lookup gid (copy ent)

        gid += 1
        ent

    fn purge (self)
        """"Removes all dead entities.
        for ent in self
            if (not ent.alive?)
                'swap self._entities ((countof self._entities) - 1)
                'pop self._entities
                'discard self._entity-lookup ent.id

    fn update (self)
        for ent in self
            for component in ent.components
                'update component ent

let EntityConstructor = (@ (function (uniqueof Entity -1)))
let ArchetypeMap = (Map EntityKind EntityConstructor)
typedef+ ArchetypeMap
    fn get (self kind)
        imply kind EntityKind
        # NOTE: because the inputs to this map are well defined, we can
        # fearlessly assert false on error, meaning I messed up in the definition
        # of the archetypes, and not in whatever code I'm writing that uses them.
        try
            super-type.get self kind
        else
            assert false "unknown entity type"
            unreachable;

global archetypes : ArchetypeMap

inline set-archetype (tag f)
    'set archetypes tag (static-typify f)

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
