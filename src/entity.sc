using import .radlib.core-extensions
using import .radlib.stringtools
let reflection = (import .radlib.reflection)

using import .common
import .renderer
using import .component
import .collision
import .event-system
import .config

using import enum
using import struct
using import glm
using import String
using import Map
using import Array
using import Rc

let EntityId = u32

struct Entity
    id : EntityId
    tag : EntityKind = EntityKind.Player
    # if false, can be removed at any moment
    alive? : bool = true
    position : vec2
    velocity : vec2
    grounded? : bool
    components : ComponentList

    inline get-component (self name)
        try
            'get self.components name
        else
            static-if config.AOT_MODE?
                assert false
                    as
                        build-String
                            "tried to get a component "
                            (tostring name)
                            " that was not part of the entity."
                        rawstring
            else
                assert false
                    ..
                        "tried to get a component "
                        repr name
                        " that was not part of entity "
                        repr self.tag

            unreachable;

    inline has-component? (self name)
        'in? self.components name

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
        # iterate backwards so our deletions don't affect results
        for i in (rrange ((countof self._entities) - 1))
            let ent = (self._entities @ i)
            let id = (deref ent.id) # will change after the swap!
            if (not ent.alive?)
                for name component in ent.components
                    'destroy component ent
                'swap self._entities i ((countof self._entities) - 1)
                'pop self._entities
                'discard self._entity-lookup id

    fn init (self)
        for ent in self
            for name component in ent.components
                'init component ent

    fn update (self dt)
        for ent in self
            for name component in ent.components
                'update component ent dt

        for ent in self
            for name component in ent.components
                'post-update component ent dt

        using event-system
        inline fire-events (evtype callback-name)
            let events = (poll-events evtype)

            inline fire-event (ent source payload)
                for name super-component in ent.components
                    'apply super-component
                        inline (ft component)
                            let T = (elementof ft.Type 0)
                            static-if (reflection.has-symbol? T callback-name)
                                callback-name component ent source (unpack payload)

            for ev in events
                # components.sc can't access the entity list to do entity lookups, so we must
                # provide them with the entity directly.
                let source =
                    try ('get self._entity-lookup ev.source)
                    else (continue) # entity is already dead!

                # respond to events
                # TODO: use something less dangerous as a sentinel value, maybe an enum.
                # If target is this special value, then every entity receives the event.
                if (ev.target == -1:u32)
                    for ent in self._entities
                        fire-event ent source ev.payload
                else
                    let target =
                        try ('get self._entity-lookup ev.target)
                        else (continue) # entity is already dead!
                    fire-event target source ev.payload

        fire-events EventType.Collision 'on-collision
        fire-events EventType.TriggerEnter 'on-trigger-enter
        fire-events EventType.TriggerExit 'on-trigger-exit

        'purge self

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

inline get-sprite-group (group-name)
    try
        'get renderer.sprite-metadata (String group-name)
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
                        components.ActionPuppet
                            hp = 10
                            max-hp = 10
                        components.PlayerController;

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
                            trigger? = false
                        components.ActionPuppet
                            hp = 2
                        components.DuckyBehaviour;

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

    set-archetype EntityKind.Coin
        fn ()
            sprite := (get-sprite-group "adve") @ 3
            let hitbox-size = (copy sprite.scale)

            Entity
                tag = EntityKind.Coin
                components =
                    ComponentList
                        components.Sprite
                            layer = 0
                            sprite
                        components.Hitbox
                            size = hitbox-size
                            collider = (Rc.wrap (collision.Collider))
                            trigger? = true
                        components.CoinBehaviour 1

do
    let EntityKind EntityId Entity EntityList EntityError archetypes init-archetypes
    locals;
