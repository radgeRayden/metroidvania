using import enum
using import struct
using import Array
using import Rc
using import glm
using import Map

using import .radlib.core-extensions
import .common
import .renderer
import .collision
import .event-system
import .input
import .config

global show-msgbox : bool

typedef ComponentBase < Struct
    fn init (...)
        ;
    fn update (...)
        ;
    fn post-update (...)
        ;
    fn draw (...)
        ;
    fn destroy (...)
        ;

# COMPONENT DEFINITIONS
# ================================================================================
enum Component
vvv bind components
do
    struct Undefined < ComponentBase
        """"Default state of Component when no subtype is supplied to the
            constructor.
        dummy-field : u8 # just so we aren't empty

    struct Sprite < ComponentBase
        layer : u32
        sprite : common.Sprite

        fn update (self owner dt)
            self.sprite.position = (floor owner.position)
            ;

        fn draw (self owner)
            'add (renderer.sprite-layers @ self.layer) self.sprite
            ;

    struct Hitbox < ComponentBase
        offset : vec2
        size : vec2
        collider : (Rc collision.Collider)
        trigger? : bool

        fn init (self owner)
            aabb-min := owner.position + self.offset
            aabb-max := aabb-min + self.size
            self.collider.id = owner.id
            self.collider.aabb =
                typeinit
                    aabb-min
                    aabb-max

            if (not self.trigger?)
                collision.register-object (copy self.collider)
            else
                collision.register-trigger (copy self.collider)
            ;

        fn destroy (self owner)
            if self.trigger?
                collision.remove-trigger owner.id
            else
                collision.remove-object owner.id
            ;

    struct MessageBoxTrigger < ComponentBase
        msg-index : u32

        fn on-trigger-enter (self owner other args...)
            using common
            if (other.tag == EntityKind.Player)
                show-msgbox = true
            owner.alive? = false

        fn on-trigger-exit (self owner other args...)
            using common
            if (other.tag == EntityKind.Player)
                show-msgbox = false

    struct CoinBehaviour < ComponentBase
        value : u32

        fn on-trigger-enter (self owner other args...)
            let soloud = (import .FFI.soloud)
            import .sound
            let sfxr = (soloud.Sfxr_create)
            soloud.Sfxr_loadPreset sfxr soloud.SFXR_COIN 1000
            soloud.play sound.soloud-instance sfxr

            owner.alive? = false
            ;

        fn on-trigger-exit (self owner other args...)
            ;

    struct ActionPuppet < ComponentBase
        velocity : vec2
        hp : i32
        gravity-factor : f32 = 1.0
        grounded? : bool

        _hitbox : (Rc Component)

        fn init (self owner)
            self._hitbox = (copy ('get-component owner 'Hitbox))

        fn post-update (self owner dt)
            if (self.hp < 0)
                # TODO: trigger death event
                ;
            hitbox := self._hitbox as Hitbox

            let yvel = self.velocity.y
            let xvel = self.velocity.x

            # apply gravity
            if ((deref self.grounded?) and (yvel <= 0))
                yvel = 0
            else
                yvel = (clamp (yvel + (config.GRAVITY * self.gravity-factor * dt)) -100. 200.)

            # perform collision aware movement
            hitbox := (self._hitbox as Hitbox)
            'try-move hitbox.collider
                owner.position + self.velocity * dt

            owner.position = hitbox.collider.Position

            local probe =
                collision.Collider
                    id = owner.id
                    aabb =
                        typeinit
                            min = (hitbox.collider.aabb.min + 0.1)
                            max = (hitbox.collider.aabb.max - 0.1)
            probe.aabb = ('project probe.aabb (probe.Position - (vec2 0 1)))
            self.grounded? =
                collision.test-intersection probe
            ;

        fn on-collision (self owner source normal contact ...)
            let normal contact =
                'extract normal 'Direction
                'extract contact 'Position

            if (normal.y > 0)
                self.velocity.y = 0
            if (normal.x != 0)
                self.velocity.x = 0


    # mockup of enemy AI
    struct DuckyBehaviour < ComponentBase
        _puppet : (Rc Component)

        fn init (self owner)
            self._puppet = (copy ('get-component owner 'ActionPuppet))
            ;

        fn update (self owner dt)
            puppet := self._puppet as ActionPuppet
            # copied from boot.update
            let yvel = puppet.velocity.y
            let xvel = puppet.velocity.x
            # apply gravity
            if ((deref puppet.grounded?) and (yvel <= 0))
                yvel = -1
            else
                yvel = (clamp (yvel + (config.GRAVITY * dt)) -100. 200.)
            ;

    struct PlayerController < ComponentBase
        _puppet : (Rc Component)

        let JumpForce = 120.
        let Speed = 40.
        let Acceleration = 180.

        fn init (self owner)
            self._puppet = (copy ('get-component owner 'ActionPuppet))
            ;

        fn update (self owner dt)
            puppet := self._puppet as ActionPuppet
            if (input.pressed? 'A)
                if puppet.grounded?
                    puppet.velocity.y = JumpForce
                    puppet.grounded? = false

            let yvel = puppet.velocity.y
            let xvel = puppet.velocity.x
            if (input.down? 'Left)
                if (xvel > 0)
                    xvel = 0
                xvel = (max -Speed (xvel - Acceleration * dt))
            elseif (input.down? 'Right)
                if (xvel < 0)
                    xvel = 0
                xvel = (min Speed (xvel + Acceleration * dt))
            else
                friction := -Acceleration * (sign xvel) * 1.5
                new-xvel := xvel + friction * dt
                if ((sign xvel) != (sign new-xvel))
                    xvel = 0
                else
                    xvel = new-xvel
            ;

    locals;

# I need this for some reason
run-stage;

# INTERFACE
# ================================================================================
using import .radlib.class

class Component
    use components

    inline init (self owner)
        'apply self
            (T self) -> ('init self owner)

    inline update (self owner dt)
        'apply self
            (T self) -> ('update self owner dt)

    inline post-update (self owner dt)
        'apply self
            (T self) -> ('post-update self owner dt)

    inline draw (self owner)
        'apply self
            (T self) -> ('draw self owner)

    inline destroy (self owner)
        'apply self
            (T self) -> ('destroy self owner)

'append Component.__typecall
    inline... (cls : type)
        Component.Undefined;

let ComponentList = (Map Symbol (Rc Component))
typedef+ ComponentList
    inline __typecall (cls ...)
        local self = (super-type.__typecall cls)
        va-map
            inline (c)
                let super = (Rc.wrap (Component c))
                'apply super
                    inline (T __)
                        'set self T.Name super
            ...
        deref self
do
    let
        Component
        ComponentList
        components

        show-msgbox
    locals;
