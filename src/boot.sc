using import .radlib.core-extensions
using import .radlib.stringtools

using import enum
using import struct
using import glm
using import Array
using import Rc
using import Map
using import String
using import itertools
using import Option

import .math
import .entity
import .filesystem
using import .common
import .renderer
using renderer
using constants
import .collision
import .component

let argc argv = (launch-args)

# DEPENDENCIES
# ================================================================================
# prototype mode
if (not BUILD_MODE_AMALGAMATED?)
    switch operating-system
    case 'linux
        load-library "../lib/libgame.so"
        load-library "../lib/libglfw.so"
        load-library "../lib/libphysfs.so"
        load-library "../lib/cimgui.so"
    case 'windows
        load-library "../lib/libgame.dll"
        load-library "../lib/glfw3.dll"
        load-library "../lib/libphysfs.dll"
        load-library "../lib/cimgui.dll"
    default
        error "Unsupported OS."

run-stage;

let C = (import .radlib.libc)

let glfw = (import .FFI.glfw)
let gl = (import .FFI.glad)
let stbi = (import .FFI.stbi)
let cjson = (import .FFI.cjson)
let c2 = (import .FFI.c2)
let ig = (import .FFI.imgui)

inline json-array->generator (arr)
    Generator
        inline "start" ()
            arr.child
        inline "valid?" (self)
            self != null
        inline "at" (self)
            self
        inline "next" (self)
            self.next

typedef+ (mutable@ cjson.cJSON)
    inline __as (selfT otherT)
        static-if (otherT == Generator)
            json-array->generator

run-stage;

# DEPENDENCY INITIALIZATION
# ================================================================================

# NOTE: subsequent modules depend on it, so it must be initialized first.
filesystem.init argv

glfw.SetErrorCallback
    fn "glfw-error" (error-code message)
        assert false (string message)

glfw.Init;
glfw.WindowHint glfw.GLFW_CLIENT_API glfw.GLFW_OPENGL_API
glfw.WindowHint glfw.GLFW_DOUBLEBUFFER true
glfw.WindowHint glfw.GLFW_OPENGL_FORWARD_COMPAT true
glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MAJOR 4
glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MINOR 5
glfw.WindowHint glfw.GLFW_OPENGL_DEBUG_CONTEXT true
glfw.WindowHint glfw.GLFW_OPENGL_PROFILE glfw.GLFW_OPENGL_CORE_PROFILE
# glfw.WindowHint glfw.GLFW_SAMPLES 4

let main-window = (glfw.CreateWindow 1280 720 ("untitled metroidvania - " .. GAME_VERSION) null null)
if (main-window == null)
    error "Failed to create a window with specified settings."
glfw.MakeContextCurrent main-window
glfw.SwapInterval 1

renderer.init main-window

ig.CreateContext null
local io = (ig.GetIO)

ig.impl.Glfw_InitForOpenGL main-window true
ig.impl.OpenGL3_Init null

run-stage;

# HELPERS AND TYPES
# ================================================================================
struct TileProperties
    solid? : bool

struct Tileset
    image : String
    tile-width : u32
    tile-height : u32
    tile-properties : (Array TileProperties)

    global tileset-cache : (Map String (Rc this-type))
    inline __typecall (cls filename)
        fn load-tiled-tileset (filename)
            let data = (filesystem.load-full-file filename)
            let json-data = (cjson.ParseWithLength data (countof data))
            let image-name =
                cjson.GetStringValue
                    cjson.GetObjectItem json-data "image"
            let tile-width =
                deref
                    (cjson.GetObjectItem json-data "tilewidth") . valueint
            let tile-height =
                deref
                    (cjson.GetObjectItem json-data "tileheight") . valueint

            let image-path =
                (String "levels/") .. (String image-name (C.string.strlen image-name))

            let tiles = (cjson.GetObjectItem json-data "tiles")
            let tile-count =
                deref ((cjson.GetObjectItem json-data "tilecount") . valueint)

            local tile-properties : (Array TileProperties)
            'resize tile-properties tile-count

            for t in (json-array->generator tiles)
                inline gci (obj name) # get child item
                    cjson.GetObjectItem obj name

                id := (gci t "id") . valueint
                let cur-tile-props = (tile-properties @ id)
                let props = (gci t "properties")
                for p in (json-array->generator props)
                    let name =
                        do
                            let raw = (cjson.GetStringValue (gci p "name"))
                            String raw (C.string.strlen raw)
                    match name
                    case "solid"
                        cur-tile-props.solid? = (((gci p "value") . valueint) as bool)
                    default
                        ;

            cjson.Delete json-data

            super-type.__typecall cls
                image = image-path
                tile-width = (tile-width as u32)
                tile-height = (tile-height as u32)
                tile-properties = (deref tile-properties)
        try
            copy
                'get tileset-cache filename
        else
            let new-tileset =
                Rc.wrap (load-tiled-tileset filename)
            let result = (copy new-tileset)
            'set tileset-cache filename new-tileset
            result

    fn clear-cache ()
        'clear tileset-cache

struct Scene
    tileset : (Rc Tileset)
    width : u32
    height : u32
    level-data : (Array u32)
    # NOTE: for now we're assuming the scene has no offset, single layer, etc.
    # A lot of these assumptions will not hold eventually, but for now we can afford
    # to simply fill this in with tile solid value and do collision detection based on
    # that. At some point it might be better to do something like kikito's bump.
    collision-matrix : (Array bool)
    background-sprites : SpriteBatch
    entities : entity.EntityList

    inline... __typecall (cls filename)
        fn load-tiled-level (filename)
            let tiled-scene = (cjson.Parse (filesystem.load-full-file filename))

            # we'll assume a single tileset per level atm
            let tileset =
                cjson.GetArrayItem
                    cjson.GetObjectItem tiled-scene "tilesets"
                    0

            let basedir = (String "levels/")
            let tileset-path =
                cjson.GetStringValue
                    cjson.GetObjectItem tileset "source"

            let tileset-full-path =
                basedir .. (String tileset-path (C.string.strlen tileset-path))
            let tileset-obj = (Tileset tileset-full-path)

            # we have to deref since those are references to the json object
            let scene-width-tiles scene-height-tiles =
                deref ((cjson.GetObjectItem tiled-scene "width") . valueint)
                deref ((cjson.GetObjectItem tiled-scene "height") . valueint)

            let scene-width-px scene-height-px =
                (scene-width-tiles as u32) * (copy tileset-obj.tile-width)
                (scene-height-tiles as u32) * (copy tileset-obj.tile-height)

            # NOTE: assuming we only have one layer. Will improve this to handle
            # multiple layers when it's needed.
            let level-layer =
                cjson.GetArrayItem
                    cjson.GetObjectItem tiled-scene "layers"
                    0

            local level-data : (Array u32)
            local collision-matrix : (Array bool)
            let tile-array = (cjson.GetObjectItem level-layer "data")
            for tile in (json-array->generator tile-array)
                let id = (tile.valueint as u32)
                'append level-data id
                'append collision-matrix ((tileset-obj.tile-properties @ (id - 1)) . solid?)

            let tile-width tile-height = tileset-obj.tile-width tileset-obj.tile-height
            local background-sprites =
                SpriteBatch tileset-obj.image tile-width tile-height
            for i x y in (enumerate (dim scene-width-tiles scene-height-tiles))
                let tile = (level-data @ i)
                let scale =
                    if (tile == 0)
                        vec2; # invisible tile
                    else
                        vec2 tile-width tile-height
                'add background-sprites
                    Sprite
                        position =
                            vec2
                                tileset-obj.tile-width * (x as u32)
                                # because images go y down but we go y up
                                tileset-obj.tile-height * ((scene-height-tiles - 1 - y) as u32)
                        scale = scale
                        pivot = (vec2)
                        texcoords = (vec4 0 0 1 1)
                        page = (tile - 1)
                        rotation = 0

            local entities : entity.EntityList

            let obj-layer =
                cjson.GetArrayItem
                    cjson.GetObjectItem tiled-scene "layers"
                    1

            let obj-layer-type = (cjson.GetStringValue (cjson.GetObjectItem obj-layer "type"))
            assert ((C.string.strcmp obj-layer-type "objectgroup") == 0)
            let objects = (cjson.GetObjectItem obj-layer "objects")

            for obj in objects
                let x y =
                    cjson.GetNumberValue (cjson.GetObjectItem obj "x")
                    cjson.GetNumberValue (cjson.GetObjectItem obj "y")
                # TODO: make this a helper function outside of this and use it everywhere
                # to convert from y-down to y-up.
                inline tiled->worldpos (x y)
                    vec2
                        x
                        (scene-height-px as i32) - 1 - (y as i32)

                let props = (cjson.GetObjectItem obj "properties")
                let archetype =
                    fold (archetype = -1) for prop in props
                        let prop-name = (cjson.GetObjectItem prop "name")
                        if ((C.string.strcmp (cjson.GetStringValue prop-name) "archetype") == 0)
                            break
                                deref
                                    (cjson.GetObjectItem prop "value") . valueint
                        archetype
                if (archetype == -1)
                    # misconfigured entity
                    continue;
                let ent =
                    'add entities
                        call
                            'get entity.archetypes (archetype as entity.EntityKind)

                ent.position = (tiled->worldpos x y)

            cjson.Delete tiled-scene

            super-type.__typecall cls
                tileset = tileset-obj
                background-sprites = background-sprites
                width = scene-width-px
                height = scene-height-px
                level-data = level-data
                collision-matrix = collision-matrix
                entities = (deref entities)
        load-tiled-level filename

struct Camera plain
    position : vec2
    scale : vec2
    viewport : vec2
    _bounds : vec4

    fn world->screen (self world)
        world - self.position

    fn set-bounds (self scene-offset scene-size)
        self._bounds =
            vec4
                scene-offset
                max scene-size self.viewport

    fn follow (self target)
        # NOTE: we floor target to simulate tracking the sprite instead of the entity
        # position. This is important so our clamp can't get desync'd from the visual element
        # it's tracking.
        let target = (world->screen self (floor target))
        # define focus box
        center := self.viewport / 2
        focus-box-size := self.viewport.0y * 0.5
        f0 := center - (focus-box-size / 2)
        f1 := f0 + focus-box-size

        let snap-point = (clamp target f0 f1)
        new-pos := self.position - (snap-point - target)

        let bounds = self._bounds
        self.position =
            # max has to be adjusted because position is at bottom left corner of viewport
            clamp new-pos (imply bounds.st vec2) (bounds.pq - self.viewport)
        ;

    fn apply (self)
        let transform =
            *
                math.translate (vec3 -1 -1 0)
                # NOTE: disabled scaling for now until I sort out the relation
                # with the viewport size and whatnot.
                # math.scale self.scale.xy1
                math.ortho self.viewport.x self.viewport.y
                math.translate (floor -self.position.xy0)
        renderer.set-world-transform transform
        ;

# RESOURCE INITIALIZATION
# ================================================================================
entity.init-archetypes;

# TODO: move the tilemap sprites to a renderer sprite layer, so we can have an initialized
# version of Scene without Option, since it won't be dependent on opengl anymore.
global current-scene = (Scene "levels/1.json")
global main-camera : Camera
    position = (vec2)
    scale = (vec2 6)
    viewport = (vec2 INTERNAL_RESOLUTION)

global player : (Rc entity.Entity)
global player-collider : (Rc collision.Collider)

fn start-game ()
    try
        current-scene = (Scene "levels/1.json")
        for ent in current-scene.entities
            if (ent.tag == entity.EntityKind.Player)
                player = (copy ent)

                let hitbox =
                    'unsafe-extract-payload (player.components @ 1)
                        component.Component.Hitbox.Type
                player-collider =
                    copy hitbox.collider
                break;

        'clear collision.objects
        local matrix-copy : (Array bool)
        for el in current-scene.collision-matrix
            'append matrix-copy (copy el)
        collision.configure-level
            collision.LevelCollisionInfo
                matrix = matrix-copy
                level-size = (vec2 current-scene.width current-scene.height)
                tile-size =
                    vec2 current-scene.tileset.tile-width current-scene.tileset.tile-height
        'init current-scene.entities

    except (ex)
        'dump ex
start-game;

# GAME CODE
# ================================================================================
let jump-force = 120.
let gravity = -240.
let player-speed = 40.
let accel = 180.

global window-width : i32
global window-height : i32

global show-colliders? : bool

'set-bounds main-camera (vec2) (vec2 current-scene.width current-scene.height)

fn solid-tile? (pos)
    let tile-size = (vec2 current-scene.tileset.tile-width current-scene.tileset.tile-height)
    let lw lh =
        (current-scene.width as f32) / tile-size.x
        (current-scene.height as f32) / tile-size.y

    let t = (floor (pos / tile-size))

    # out of bounds
    if (or
        (t.x < 0)
        (t.x > lw)
        (t.y < 0)
        (t.y > lh))
        return false

    # again remember our world space is y up
    idx := (lh - 1 - t.y) * lw + t.x
    deref (current-scene.collision-matrix @ (idx as usize))

fn grounded? ()
    # NOTE: because currently the player AABB is hardcoded at 8x8, we know
    # if it clears the tiles at its 2 lower corners then it's airborne.
    let pos = player.position
    or
        solid-tile? (vec2 pos.x (pos.y - 1))
        # NOTE: adding 7 under the assumption that the interval is [begin,end)
        # it seems to solve a bug where we could climb walls, because it would always be grounded
        # as long as we were touching them to the right.
        solid-tile? (vec2 (pos.x + 7) (pos.y - 1))

fn player-move (pos)
    'try-move player-collider pos
    player.position = player-collider.Position

glfw.SetKeyCallback main-window
    fn (window _key scancode action mods)
        # application keybindings
        # exit game
        if ((_key == glfw.GLFW_KEY_ESCAPE) and (action == glfw.GLFW_RELEASE))
            glfw.SetWindowShouldClose main-window true

        # restart game
        if (and
            ((mods & glfw.GLFW_MOD_CONTROL) as bool)
            (_key == glfw.GLFW_KEY_R)
            (action == glfw.GLFW_PRESS))
            start-game;
        # go fullscreen
        if (and
            ((mods & glfw.GLFW_MOD_ALT) as bool)
            (_key == glfw.GLFW_KEY_ENTER)
            (action == glfw.GLFW_RELEASE))

            global fullscreen? : bool false
            global prev-x : i32
            global prev-y : i32
            global prev-width : i32
            global prev-height : i32

            let monitor = (glfw.GetPrimaryMonitor)
            if (not fullscreen?)
                fullscreen? = true
                glfw.GetWindowSize main-window &prev-width &prev-height
                glfw.GetWindowPos main-window &prev-x &prev-y
                let video-mode = (glfw.GetVideoMode monitor)
                glfw.SetWindowMonitor main-window monitor 0 0
                    video-mode.width
                    video-mode.height
                    glfw.GLFW_DONT_CARE as i32
            else
                fullscreen? = false
                glfw.SetWindowMonitor main-window null prev-x prev-y
                    prev-width
                    prev-height
                    glfw.GLFW_DONT_CARE as i32

        if (((mods & glfw.GLFW_MOD_ALT) as bool) and (action == glfw.GLFW_PRESS))
            let scale =
                switch _key
                case glfw.GLFW_KEY_1
                    1
                case glfw.GLFW_KEY_2
                    2
                case glfw.GLFW_KEY_3
                    3
                case glfw.GLFW_KEY_4
                    4
                case glfw.GLFW_KEY_5
                    5
                default
                    -1
            if (scale == -1)
                ; # do nothing, wrong keybinding
            else
                scaled := INTERNAL_RESOLUTION * scale
                glfw.SetWindowSize main-window scaled.x scaled.y

        if ((_key == glfw.GLFW_KEY_F3) and (action == glfw.GLFW_RELEASE))
            show-colliders? = (not show-colliders?)

        # game controls
        if ((_key == glfw.GLFW_KEY_SPACE) and (action == glfw.GLFW_PRESS))
            if player.grounded?
                player.velocity.y = jump-force
        ;

fn update (dt)
    fn key-down? (code)
        (glfw.GetKey main-window code) as bool

    player.grounded? = (grounded?)

    let yvel = player.velocity.y
    let xvel = player.velocity.x
    if (key-down? glfw.GLFW_KEY_LEFT)
        if (xvel > 0)
            xvel = 0
        xvel = (max -player-speed (xvel - accel * dt))
    elseif (key-down? glfw.GLFW_KEY_RIGHT)
        if (xvel < 0)
            xvel = 0
        xvel = (min player-speed (xvel + accel * dt))
    else
        let friction = (-accel * (sign xvel) * 1.5)
        xvel = (xvel + friction * dt)
        if ((abs xvel) <= (friction * dt))
            xvel = 0

    if (xvel != 0)
        using import .component
        sprite := ('unsafe-extract-payload (player.components @ 0) Component.Sprite.Type) . sprite
        sprite.pivot = (vec2 4)
        sprite.rotation += -xvel * dt * 0.3
    else
        using import .component
        sprite := ('unsafe-extract-payload (player.components @ 0) Component.Sprite.Type) . sprite
        sprite.rotation = 0

    # apply gravity
    # NOTE: we check for yvel <= 0 so we are still able to jump, since
    # the jump sets the yvel to be positive, but it would immediately be set to 0
    # because grounded.
    if ((deref player.grounded?) and (yvel <= 0))
        yvel = 0
    else
        yvel = (clamp (yvel + (gravity * dt)) -100. 200.)

    let fdt = (dt / 4)
    for i in (range 4)
        player-move (player.position + player.velocity * fdt)

    'follow main-camera player.position
    'update current-scene.entities dt

global debug-gizmos : (Mesh vec2 u16) 128

fn gizmo-vshader ()
    using import glsl
    buffer vertices :
        struct VertexArray plain
            data : (array vec2)

    uniform transform : mat4

    let vertex = (vertices.data @ gl_VertexID)
    gl_Position = transform * (vec4 vertex 0 1)

fn gizmo-fshader ()
    using import glsl
    out fcolor : vec4
        location = 0

    fcolor = (vec4 1 1 1 .25)

global gizmo-shader =
    renderer.GPUShaderProgram gizmo-vshader gizmo-fshader

fn draw-colliders ()
    'clear debug-gizmos.attribute-data
    'clear debug-gizmos.index-data

    import .collision

    # polyline algorithm
    for obj in collision.objects
        let aabb = obj.aabb
        let aabb-min aabb-max =
            floor (imply aabb.min vec2)
            floor (imply aabb.max vec2)
        local points =
            # 3 - 2
            # |   |
            # 0 - 1
            arrayof vec2
                aabb-min
                vec2 aabb-max.x aabb-min.y
                aabb-max
                vec2 aabb-min.x aabb-max.y
                aabb-min

        let vertex-offset = (countof debug-gizmos.attribute-data)
        for i in (range ((countof points) - 1))
            this-point := points @ i
            next-point := points @ (i + 1)

            dir := (normalize (next-point - this-point))
            perp := (math.rotate dir (pi / 2))
            'append debug-gizmos.attribute-data this-point
            'append debug-gizmos.attribute-data (this-point + perp)
            'append debug-gizmos.attribute-data next-point
            'append debug-gizmos.attribute-data (next-point + perp)

        for i in (range ((countof points) - 1))
            let segment-start = (vertex-offset + (i * 4))
            let left right left-e right-e =
                segment-start
                segment-start + 1
                segment-start + 2
                segment-start + 3
            'append debug-gizmos.index-data (left as u16)
            'append debug-gizmos.index-data (right as u16)
            'append debug-gizmos.index-data (right-e as u16)
            'append debug-gizmos.index-data (right-e as u16)
            'append debug-gizmos.index-data (left-e as u16)
            'append debug-gizmos.index-data (left as u16)

    'update debug-gizmos

    local prev-shader : i32
    gl.GetIntegerv gl.GL_CURRENT_PROGRAM &prev-shader
    gl.UseProgram gizmo-shader
    'apply main-camera
    'draw debug-gizmos
    gl.UseProgram (prev-shader as u32)

fn draw ()
    for ent in current-scene.entities
        for component in ent.components
            'draw component ent

    'apply main-camera

    'update current-scene.background-sprites.sprites
    'draw current-scene.background-sprites

global last-time = (glfw.GetTime)
while (not (glfw.WindowShouldClose main-window))
    glfw.PollEvents;
    glfw.GetFramebufferSize main-window &window-width &window-height

    global dt-accum : f64

    let time-scale = 1

    let now = (glfw.GetTime)
    let real-dt = (now - last-time)
    last-time = now
    dt-accum += real-dt * time-scale

    step-size := 1 / 60

    while (dt-accum >= step-size)
        dt-accum -= step-size
        update step-size

    renderer.begin;
    draw;
    renderer.submit;

    if show-colliders?
        draw-colliders;
    renderer.present;

    ig.impl.OpenGL3_NewFrame;
    ig.impl.Glfw_NewFrame;
    ig.NewFrame;

    global player-stats-open? : bool true
    if player-stats-open?
        ig.Begin "Debug Info" &player-stats-open? 0

        # position in tiles
        let tile-p =
            do
                let tile-dimensions =
                    vec2
                        current-scene.tileset.tile-width
                        current-scene.tileset.tile-height
                let tile-p = (ivec2 (floor (player.position / tile-dimensions)))

        ig.Text "position: %.3f %.3f (%d %d)" player.position.x player.position.y
            \ tile-p.x tile-p.y
        ig.Text "velocity: %.3f %.3f" (unpack (player.velocity * step-size))
        ig.Text f"grounded?: ${player.grounded?}"
        ig.End;

    ig.Render;
    ig.impl.OpenGL3_RenderDrawData (ig.GetDrawData)

    glfw.SwapBuffers main-window

# CLEANUP
# ================================================================================
glfw.DestroyWindow main-window
glfw.Terminate;
