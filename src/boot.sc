# TODO: import as submodule
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

renderer.init;

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

global player : (Rc entity.Entity)

# NOTE: for a game this size, I opted to load all the sprite textures
# at once, in a single ArrayTexture where they can be indexed by position in
# the atlas and page (array layer). To make this easier to work with we use
# this singleton.
fn game-texture-atlas ()
    global atlas : (Option (Rc ArrayTexture2D))
    if (not atlas)
        local images : (Array String)
        # TODO: for the moment I'm loading the test tileset
        # as if it were one of the atlas images. Should be substituted
        # for proper texture atlas, possibly with a naming convention
        # such that this list doesn't need to be filled in explicitly.
        'append images (String "levels/adve.png")
        atlas =
            Rc.wrap (ArrayTexture2D images 8 8)

    copy ('force-unwrap atlas)

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
    entity-sprites : SpriteBatch

    inline __typecall (cls filename)
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
            local background-sprites =
                SpriteBatch tileset-obj.image tileset-obj.tile-width tileset-obj.tile-height
            for i x y in (enumerate (dim scene-width-tiles scene-height-tiles))
                let tile = (level-data @ i)
                let scale =
                    if (tile == 0)
                        vec2; # invisible tile
                    else
                        vec2 1
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

            # HACK: extracting player position from the tileset objects while
            # reusing the tileset image. Later I'll have entities separate, very likely
            # using their own sprite sheet.
            let obj-layer =
                cjson.GetArrayItem
                    cjson.GetObjectItem tiled-scene "layers"
                    1
            let obj-layer-type = (cjson.GetStringValue (cjson.GetObjectItem obj-layer "type"))
            assert ((C.string.strcmp obj-layer-type "objectgroup") == 0)
            let player-obj =
                cjson.GetArrayItem
                    cjson.GetObjectItem obj-layer "objects"
                    0
            let player-obj-name = (cjson.GetStringValue (cjson.GetObjectItem player-obj "name"))
            assert ((C.string.strcmp player-obj-name "player") == 0)
            let px py =
                cjson.GetNumberValue (cjson.GetObjectItem player-obj "x")
                cjson.GetNumberValue (cjson.GetObjectItem player-obj "y")
            # TODO: make this a helper function outside of this and use it everywhere
            # to convert from y-down to y-up.
            inline tiled->worldpos (x y)
                vec2
                    x
                    (scene-height-px as i32) - 1 - (y as i32)

            player =
                copy
                    'add entities
                        call
                            'get entity.archetypes entity.EntityKind.Player

            player.position = (tiled->worldpos px py)
            # end of the hack

            cjson.Delete tiled-scene

            super-type.__typecall cls
                tileset = tileset-obj
                background-sprites = background-sprites
                width = scene-width-px
                height = scene-height-px
                level-data = level-data
                collision-matrix = collision-matrix
                entities = (deref entities)
                entity-sprites = (SpriteBatch (game-texture-atlas))
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
        let target = (world->screen self target)
        # define focus box
        center := self.viewport / 2
        focus-box-size := self.viewport * 0.5
        f0 := center - (focus-box-size / 2)
        f1 := f0 + focus-box-size

        let snap-point = (clamp target f0 f1)
        new-pos := self.position - (snap-point - target)

        let bounds = self._bounds
        self.position =
            # max has to be adjusted because position is at top left corner of viewport
            clamp new-pos (imply bounds.st vec2) (bounds.pq - self.viewport)
        ;

    fn apply (self shader)
        let transform =
            *
                math.translate (vec3 -1 -1 0)
                # NOTE: disabled scaling for now until I sort out the relation
                # with the viewport size and whatnot.
                # math.scale self.scale.xy1
                math.ortho self.viewport.x self.viewport.y
                math.translate (floor -self.position.xy0)
        gl.UniformMatrix4fv
            gl.GetUniformLocation shader "transform"
            1
            false
            (&local transform) as (pointer f32)
        ;

# RESOURCE INITIALIZATION
# ================================================================================
fn sprite-vertex-shader ()
    using import glsl
    buffer attributes :
        struct AttributeArray plain
            data : (array Sprite)

    uniform transform : mat4
    uniform layer_size : vec2

    out vtexcoord : vec3
        location = 2

    local vertices =
        arrayof vec2
            vec2 0 0 # top left
            vec2 1 0 # top right
            vec2 0 1 # bottom left
            vec2 1 1 # bottom right

    let sprites = attributes.data
    idx         := gl_VertexID
    sprite      := sprites @ (idx // 4)
    origin      := sprite.position
    vertex      := vertices @ (idx % 4)
    orientation := sprite.rotation
    pivot       := sprite.pivot
    scale       := sprite.scale
    stexcoords  := sprite.texcoords

    local texcoords =
        arrayof vec2
            stexcoords.sq
            stexcoords.pq
            stexcoords.st
            stexcoords.pt

    # TODO: explain what this does in a comment
    gl_Position =
        * transform
            vec4 (origin + pivot + (math.rotate ((vertex * layer_size * scale) - pivot) orientation)) 0 1
    vtexcoord = (vec3 (texcoords @ (idx % 4)) sprite.page)

fn sprite-fragment-shader ()
    using import glsl
    in vtexcoord : vec3
        location = 2
    out fcolor : vec4
        location = 0

    uniform sprite-tex : sampler2DArray
    fcolor = (texture sprite-tex vtexcoord)

global sprite-shader = (GPUShaderProgram sprite-vertex-shader sprite-fragment-shader)
gl.UseProgram sprite-shader

entity.init-archetypes;
global level1 = (Scene "levels/1.json")
global main-camera : Camera
    position = (vec2)
    scale = (vec2 6)
    viewport = (vec2 INTERNAL_RESOLUTION)

global main-render-target : u32
global fb-color-attachment : GPUTexture 0
global fb-depth-attachment : GPUTexture 0

# TODO: generic function to create textures
gl.GenTextures 1 (&fb-color-attachment as (mutable@ u32))
gl.BindTexture gl.GL_TEXTURE_2D fb-color-attachment
gl.TexStorage2D gl.GL_TEXTURE_2D 1
    gl.GL_SRGB8_ALPHA8
    INTERNAL_RESOLUTION.x
    INTERNAL_RESOLUTION.y

gl.GenTextures 1 (&fb-depth-attachment as (mutable@ u32))
gl.BindTexture gl.GL_TEXTURE_2D fb-depth-attachment
gl.TexStorage2D gl.GL_TEXTURE_2D 1
    gl.GL_DEPTH_COMPONENT24
    INTERNAL_RESOLUTION.x
    INTERNAL_RESOLUTION.y

gl.CreateFramebuffers 1 &main-render-target
gl.NamedFramebufferTexture main-render-target gl.GL_COLOR_ATTACHMENT0 fb-color-attachment 0
gl.NamedFramebufferTexture main-render-target gl.GL_DEPTH_ATTACHMENT fb-depth-attachment 0

let fbo-status = (gl.CheckNamedFramebufferStatus main-render-target gl.GL_FRAMEBUFFER)
assert (fbo-status == gl.GL_FRAMEBUFFER_COMPLETE) "failed creating main render target"

# GAME CODE
# ================================================================================
global window-width : i32
global window-height : i32

'set-bounds main-camera (vec2) (vec2 level1.width level1.height)

fn solid-tile? (pos)
    let tile-size = (vec2 level1.tileset.tile-width level1.tileset.tile-height)
    let lw lh =
        (level1.width as f32) / tile-size.x
        (level1.height as f32) / tile-size.y

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
    deref (level1.collision-matrix @ (idx as usize))

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
    # scene is all on positive atm, so just do whatever. Could also
    # block, but I think it might be useful to not do that (eg. to transition rooms)
    if (or
        (pos.x < 0)
        (pos.y < 0)
        (pos.x > (level1.width as f32))
        (pos.y > (level1.height as f32)))
        player.position = pos
        return;

    let tile-size = (vec2 level1.tileset.tile-width level1.tileset.tile-height)
    let lw lh =
        (level1.width as f32) / tile-size.x
        (level1.height as f32) / tile-size.y

    # find all tiles that intersect the player AABB and test against them
    init-tx := (floor (pos.x / tile-size.x))
    # FIXME: maybe we have to subtract one pixel from the maximum? Seems to be
    # inclusive, so we're going past the actual AABB a bit.
    loop (t = (vec2 init-tx (floor (pos.y / tile-size.y))))
        # player AABB is hardcoded to 8x8 for now
        if ((t.y * tile-size.y) > (pos.y + 8))
            break;
        if (t.y >= lh)
            break;
        if ((t.x * tile-size.x) > (pos.x + 8))
            repeat (vec2 init-tx (t.y + 1))
        if (t.x >= lw)
            break;

        # again remember our world space is y up
        idx := (lh - 1 - t.y) * lw + t.x
        solid? := level1.collision-matrix @ (idx as usize)

        local manifold : c2.Manifold
        if solid?
            c2.AABBtoAABBManifold
                c2.AABB (c2.v (unpack pos)) (c2.v (unpack (pos + (vec2 8))))
                c2.AABB
                    c2.v (unpack (t * tile-size))
                    c2.v (unpack ((t * tile-size) + tile-size))
                &manifold
        if (manifold.count > 0)
            # TODO: investigate if we should resolve based on the velocity, and not only
            # informed by the normal. The manifold algorithm reports the normal
            # based on the smallest axis of penetration, which can give misleading normals
            # when colliding with corners of boxes.
            # Another path is to resolve collision multiple times (maybe up to a maximum) until
            # we are completely clear. At the moment we correct once and then exit, which might leave
            # our player character still penetrating something, causing a weird collision to be
            # reported next frame. This is probably the cause of bugs such as getting stuck
            # when bumping on the ceiling and sometimes sliding down walls.
            let normal = (vec2 manifold.n.x manifold.n.y)
            if (normal.y < 0)
                # TODO: set a grounded flag
                player.velocity.y = 0
            if (normal.x != 0)
                player.velocity.x = 0
            let depth = (manifold.depths @ 0)
            player.position = (pos - (normal * depth))
            return;

        t + (vec2 1 0)

    player.position = pos

let jump-force = 120.
let gravity = -240.
let player-speed = 60.
let accel = 180.

glfw.SetKeyCallback main-window
    fn (window _key scancode action mods)
        # application keybindings
        # exit game
        if ((_key == glfw.GLFW_KEY_ESCAPE) and (action == glfw.GLFW_RELEASE))
            glfw.SetWindowShouldClose main-window true

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
        if ((abs xvel) < 1.5)
            xvel = 0

    # apply gravity
    # NOTE: we check for yvel <= 0 so we are still able to jump, since
    # the jump sets the yvel to be positive, but it would immediately be set to 0
    # because grounded.
    if ((deref player.grounded?) and (yvel <= 0))
        yvel = 0
    else
        yvel = (clamp (yvel + (gravity * dt)) -100. 200.)
    player-move (player.position + (vec2 (player.velocity.x * dt) 0))
    player-move (player.position + (vec2 0 (player.velocity.y * dt)))

    'follow main-camera player.position
    'update level1.entities

fn draw ()
    gl.Viewport 0 0 INTERNAL_RESOLUTION.x INTERNAL_RESOLUTION.y
    gl.ClearColor 1.0 0.2 0.2 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    gl.Uniform2f
        gl.GetUniformLocation sprite-shader "layer_size"
        level1.tileset.tile-width as f32
        level1.tileset.tile-height as f32

    for layer in renderer.sprite-layers
        'clear layer
    for ent in level1.entities
        for component in ent.components
            'draw component ent

    'apply main-camera sprite-shader

    'update level1.background-sprites.sprites
    'draw level1.background-sprites

    for layer in renderer.sprite-layers
        'update layer.sprites
        'draw layer

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

    gl.BindFramebuffer gl.GL_FRAMEBUFFER main-render-target
    draw;
    gl.BindFramebuffer gl.GL_FRAMEBUFFER 0

    gl.ClearColor 0.005 0.005 0.005 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    # preserve aspect ratio
    let game-aspect-ratio = (INTERNAL_RESOLUTION.x / INTERNAL_RESOLUTION.y)
    let window-aspect-ratio = (window-width / window-height)

    let blit-size blit-offset =
        do
            window-width as:= f32
            window-height as:= f32
            if (window-aspect-ratio > game-aspect-ratio)
                blit-width := window-height * game-aspect-ratio
                _
                    ivec2 blit-width window-height
                    ivec2 ((window-width - blit-width) / 2) 0
            else
                blit-height := window-width / game-aspect-ratio
                _
                    ivec2 window-width blit-height
                    ivec2 0 ((window-height - blit-height) / 2)

    let blit-begin blit-end = blit-offset (blit-offset + blit-size)

    gl.BlitNamedFramebuffer main-render-target 0
        # src rect
        0
        0
        INTERNAL_RESOLUTION.x
        INTERNAL_RESOLUTION.y
        # dest rect
        blit-begin.x
        blit-begin.y
        blit-end.x
        blit-end.y
        gl.GL_COLOR_BUFFER_BIT
        gl.GL_NEAREST

    ig.impl.OpenGL3_NewFrame;
    ig.impl.Glfw_NewFrame;
    ig.NewFrame;

    global player-stats-open? : bool true
    if player-stats-open?
        ig.Begin "Debug Info" &player-stats-open? 0
        ig.Text "position: %.3f %.3f" player.position.x player.position.y
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
