using import .radlib.core-extensions

using import .common
import .renderer
using import .component

using import enum
using import struct
using import glm
using import String
using import Map
using import Array
using import Rc

let EntityId = u32

enum EntityKind plain
    Player = 0
    Ducky = 1
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
    components : ComponentList

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

fn get-texcoords (group-name)
    try
        'get renderer.sprite-metadata group-name
    else
        assert false "unknown sprite group"
        unreachable;

fn init-archetypes ()
    set-archetype EntityKind.Player
        fn ()
            Entity
                tag = EntityKind.Player
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            Sprite
                                page = 0
                                texcoords = ((get-texcoords "adve") @ 1)
    set-archetype EntityKind.Ducky
        fn ()
            Entity
                tag = EntityKind.Ducky
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            Sprite
                                page = 0
                                texcoords = ((get-texcoords "adve") @ 0)
    locals;

do
    let EntityKind EntityId Entity EntityList EntityError archetypes init-archetypes
    locals;
