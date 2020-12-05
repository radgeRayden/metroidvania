using import .radlib.core-extensions

using import .common
import .renderer
using import .component
import .collision
import .event-system

using import enum
using import struct
using import glm
using import String
using import Map
using import Array
using import Rc

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

let EntityId = u32

enum EntityKind plain
    Player = 0
    Ducky = 1
    Skeleton = 2
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

    fn init (self)
        for ent in self
            for component in ent.components
                'init component ent

    fn update (self dt)
        using event-system
        inline fire-events (evtype callback-name)
            let events = (poll-events evtype)

            for ev in events
                # respond to events
                # TODO: use something less dangerous as a sentinel value, maybe an enum.
                # If target is this special value, then every entity receives the event.
                if (ev.target == -1:u32)
                    for ent in self._entities
                        for super-component in ent.components
                            'apply super-component
                                inline (ft component)
                                    let T = (elementof ft.Type 0)
                                    static-if (has-symbol? T callback-name)
                                        callback-name component ev.payload
                else
                    let target =
                        try ('get self._entity-lookup ev.target)
                        else (continue) # entity is already dead!
                    for super-component in target.components
                        'apply super-component
                            inline (ft component)
                                let T = (elementof ft.Type 0)
                                static-if (has-symbol? T callback-name)
                                    callback-name component ev.payload

        fire-events EventType.Collision 'on-collision

        for ent in self
            for component in ent.components
                'update component dt ent

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

fn get-sprite-group (group-name)
    try
        'get renderer.sprite-metadata group-name
    else
        assert false "unknown sprite group"
        unreachable;

fn init-archetypes ()
    set-archetype EntityKind.Player
        fn ()
            sprite := (get-sprite-group "adve") @ 1
            let hitbox-size = (copy sprite.scale)

            Entity
                tag = EntityKind.Player
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            sprite
                        components.Hitbox
                            size = hitbox-size
                            collider = (Rc.wrap (collision.Collider))
    set-archetype EntityKind.Ducky
        fn ()
            sprite := (get-sprite-group "adve") @ 0
            let hitbox-size = (copy sprite.scale)

            Entity
                tag = EntityKind.Ducky
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            sprite
                        components.Hitbox
                            size = hitbox-size
                            collider = (Rc.wrap (collision.Collider))

    set-archetype EntityKind.Skeleton
        fn ()
            Entity
                tag = EntityKind.Skeleton
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            (get-sprite-group "Skeleton_Walk") @ 2
                        components.Hitbox
                            offset = (vec2 14 0)
                            size = (vec2 11 22)
                            collider = (Rc.wrap (collision.Collider))
    locals;

do
    let EntityKind EntityId Entity EntityList EntityError archetypes init-archetypes
    locals;
