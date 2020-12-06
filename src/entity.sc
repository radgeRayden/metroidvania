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
    Tilemap = 3
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
        inline fire-events (evtype callback-name expected-payload)
            let events = (poll-events evtype)

            inline fire-event (ent payload)
                for super-component in ent.components
                    'apply super-component
                        inline (ft component)
                            let T = (elementof ft.Type 0)
                            static-if (has-symbol? T callback-name)
                                callback-name component payload
            for ev in events
                assert (('literal ev.payload) == expected-payload.Literal)
                # components.sc can't access the entity list to do entity lookups, so we must
                # provide them with the entity directly.
                let payload =
                    static-if (expected-payload == EventPayload.EntityId)
                        try
                            'get self._entity-lookup
                                'unsafe-extract-payload ev.payload expected-payload.Type
                        else (continue)
                    else
                        ('unsafe-extract-payload ev.payload expected-payload.Type)
                # respond to events
                # TODO: use something less dangerous as a sentinel value, maybe an enum.
                # If target is this special value, then every entity receives the event.
                if (ev.target == -1:u32)
                    for ent in self._entities
                        fire-event ent payload
                else
                    let target =
                        try ('get self._entity-lookup ev.target)
                        else (continue) # entity is already dead!
                    fire-event target payload

        fire-events EventType.Collision 'on-collision EventPayload.EntityId
        fire-events EventType.TriggerEnter 'on-trigger-enter EventPayload.EntityId
        fire-events EventType.TriggerExit 'on-trigger-exit EventPayload.EntityId

        for ent in self
            for component in ent.components
                'update component ent dt

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
                            trigger? = true
                        components.MessageBoxTrigger 0

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
    set-archetype EntityKind.Tilemap
        fn ()
            Entity
                tag = EntityKind.Tilemap
    locals;

do
    let EntityKind EntityId Entity EntityList EntityError archetypes init-archetypes
    locals;
