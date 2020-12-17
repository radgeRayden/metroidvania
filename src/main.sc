using import .radlib.core-extensions

using import struct
using import glm
using import Array
using import Rc
using import Map
using import String
using import itertools

import .math
import .entity
import .filesystem
import .renderer
import .collision
import .component
import .sound
import .input
using import .common
import .config
using import .strings
import .io

let glfw = (import .FFI.glfw)
let cjson = (import .FFI.cjson)
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

semantically-bind-types ig.ImVec2 vec2
    inline "conv-from" (self)
        vec2 self.x self.y
    inline "conv-to" (other)
        ig.ImVec2 other.x other.y

struct TileProperties
    solid? : bool

struct Tileset
    image : String
    tile-width : u32
    tile-height : u32
    tile-properties : (Array TileProperties)

    global tileset-cache : (Map String (Rc this-type))
    inline... __typecall (cls)
        super-type.__typecall cls
    case (cls filename)
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
                (String "levels/") .. (String image-name (countof image-name))

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
                    let name = (cjson.GetStringValue (gci p "name"))
                    if ('equals? name "solid")
                        cur-tile-props.solid? = (((gci p "value") . valueint) as bool)

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
    entities : entity.EntityList

    inline... __typecall (cls)
        super-type.__typecall cls
    case (cls filename)
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
                basedir .. (String tileset-path (countof tileset-path))
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

            from renderer let SpriteBatch
            'clear renderer.background-layers
            'append renderer.background-layers
                SpriteBatch tileset-obj.image tile-width tile-height

            local entities : entity.EntityList

            # we use a special entity to tie scene colliders to the scene tilemap. Later
            # I might want to extend this so we can identify individual tiles.
            let tilemap-entity =
                'add entities
                    call
                        'get entity.archetypes entity.EntityKind.Tilemap

            'clear collision.objects
            'clear collision.triggers
            for i x y in (enumerate (dim scene-width-tiles scene-height-tiles))
                let tile = (level-data @ i)
                let tile-position =
                    vec2
                        tileset-obj.tile-width * (x as u32)
                        # because images go y down but we go y up
                        tileset-obj.tile-height * ((scene-height-tiles - 1 - y) as u32)

                # add scene colliders
                if ((tileset-obj.tile-properties @ (tile - 1)) . solid?)
                    let col =
                        collision.Collider
                            id = tilemap-entity.id
                            aabb =
                                typeinit
                                    # slight bias so colliders don't overlap
                                    tile-position + 0.001
                                    tile-position + (vec2 tileset-obj.tile-width tileset-obj.tile-height)
                    collision.register-object (Rc.wrap (deref col))

                let scale =
                    if (tile == 0)
                        vec2; # invisible tile
                    else
                        vec2 tile-width tile-height
                'add (renderer.background-layers @ 0)
                    Sprite
                        position = tile-position
                        scale = scale
                        pivot = (vec2)
                        texcoords = (vec4 0 0 1 1)
                        page = (tile - 1)
                        rotation = 0

            let obj-layer =
                cjson.GetArrayItem
                    cjson.GetObjectItem tiled-scene "layers"
                    1

            let obj-layer-type = (cjson.GetStringValue (cjson.GetObjectItem obj-layer "type"))
            assert ('equals? obj-layer-type "objectgroup")
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
                        if ('equals? (cjson.GetStringValue prop-name) "archetype")
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

global show-debug-info? : bool true
global show-colliders? : bool
global window-width : i32
global window-height : i32
global current-scene : Scene
global main-camera : Camera
    position = (vec2)
    scale = (vec2 6)
    viewport = (vec2 config.INTERNAL_RESOLUTION)

global player : (Rc entity.Entity)
global player-collider : (Rc collision.Collider)

fn start-game ()
    try
        current-scene = (Scene (String "levels/1.json"))
        using component
        for ent in current-scene.entities
            if (ent.tag == entity.EntityKind.Player)
                player = (copy ent)
                hitbox := ('get-component player 'Hitbox) as components.Hitbox
                player-collider = (copy hitbox.collider)
                break;

        # NOTE: code left out commented in case I decide to use tile lookup again.
        # local matrix-copy : (Array bool)
        # for el in current-scene.collision-matrix
        #     'append matrix-copy (copy el)
        # collision.configure-level
        #     collision.LevelCollisionInfo
        #         matrix = matrix-copy
        #         level-size = (vec2 current-scene.width current-scene.height)
        #         tile-size =
        #             vec2 current-scene.tileset.tile-width current-scene.tileset.tile-height
        'init current-scene.entities
        'set-bounds main-camera (vec2) (vec2 current-scene.width current-scene.height)

    except (ex)
        ;

fn keybind-handler (window _key scancode action mods)
    # application keybindings
    # exit game
    if ((_key == glfw.GLFW_KEY_ESCAPE) and (action == glfw.GLFW_RELEASE))
        glfw.SetWindowShouldClose window true

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
            glfw.GetWindowSize window &prev-width &prev-height
            glfw.GetWindowPos window &prev-x &prev-y
            let video-mode = (glfw.GetVideoMode monitor)
            glfw.SetWindowMonitor window monitor 0 0
                video-mode.width
                video-mode.height
                glfw.GLFW_DONT_CARE as i32
        else
            fullscreen? = false
            glfw.SetWindowMonitor window null prev-x prev-y
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
            scaled := config.INTERNAL_RESOLUTION * scale
            glfw.SetWindowSize window scaled.x scaled.y

    if ((_key == glfw.GLFW_KEY_F3) and (action == glfw.GLFW_RELEASE))
        show-colliders? = (not show-colliders?)
    if ((_key == glfw.GLFW_KEY_F4) and (action == glfw.GLFW_PRESS))
        show-debug-info? = (not show-debug-info?)

fn update (dt)
    using component
    let yvel = player.velocity.y
    let xvel = player.velocity.x
    player-sprite := ('get-component player 'Sprite) as components.Sprite
    let sprite = player-sprite.sprite
    if (xvel != 0)
        sprite.pivot = (vec2 4)
        sprite.rotation += -xvel * dt * 0.3
    else
        sprite.rotation = 0

    'follow main-camera player.position
    'update current-scene.entities dt

fn draw ()
    for ent in current-scene.entities
        for name component in ent.components
            'draw component ent

    'apply main-camera

# COLLIDER DEBUG DRAWING
# ================================================================================
struct 2DVertex plain
    position : vec2
    color : vec4

global debug-gizmos : (renderer.Mesh 2DVertex u16)

fn gizmo-vshader ()
    using import glsl
    buffer vertices :
        struct VertexArray plain
            data : (array 2DVertex)

    out vcolor : vec4
        location = 0

    uniform transform : mat4

    vcolor = ((vertices.data @ gl_VertexID) . color)
    let vertex = ((vertices.data @ gl_VertexID) . position)
    gl_Position = transform * (vec4 vertex 0 1)

fn gizmo-fshader ()
    using import glsl
    in vcolor : vec4
        location = 0
    out fcolor : vec4
        location = 0

    fcolor = vcolor

global gizmo-shader : renderer.GPUShaderProgram

global debug-selected-entity : u32 -1
fn draw-colliders ()
    'clear debug-gizmos.attribute-data
    'clear debug-gizmos.index-data

    import .collision

    fn draw-collider (obj color)
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

            inline make-vertex (pos)
                2DVertex pos color
            'append debug-gizmos.attribute-data (make-vertex this-point)
            'append debug-gizmos.attribute-data (make-vertex (this-point + perp))
            'append debug-gizmos.attribute-data (make-vertex next-point)
            'append debug-gizmos.attribute-data (make-vertex (next-point + perp))

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
    # polyline algorithm
    for obj in collision.objects
        let color =
            if (obj.id == (debug-selected-entity as u32))
                vec4 0 1 0 1
            else
                vec4 1 1 1 0.25
        draw-collider obj color
    for trigger in collision.triggers
        draw-collider trigger.collider (vec4 1 0 0 0.25)

    'update debug-gizmos

    let gl = (import .FFI.glad)
    local prev-shader : i32
    gl.GetIntegerv gl.GL_CURRENT_PROGRAM &prev-shader
    gl.UseProgram gizmo-shader
    'apply main-camera
    'draw debug-gizmos
    gl.UseProgram (prev-shader as u32)
# /COLLIDER DEBUG DRAWING

fn main (argc argv)
    static-if config.AOT_MODE?
        raising Nothing
    filesystem.init argv
    glfw.SetErrorCallback
        fn "glfw-error" (error-code message)
            io.log "GLFW error: %s\n" message
            switch error-code
            case glfw.GLFW_API_UNAVAILABLE
                io.log "OpenGL is not available on this system.\n"
            case glfw.GLFW_VERSION_UNAVAILABLE
                io.log "The minimum required OpenGL version of 4.2 is not available.\n"
            default
                ;

    glfw.Init;
    glfw.WindowHint glfw.GLFW_CLIENT_API glfw.GLFW_OPENGL_API
    glfw.WindowHint glfw.GLFW_DOUBLEBUFFER true
    glfw.WindowHint glfw.GLFW_OPENGL_FORWARD_COMPAT true
    glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MAJOR 4
    glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MINOR 2
    glfw.WindowHint glfw.GLFW_OPENGL_DEBUG_CONTEXT true
    glfw.WindowHint glfw.GLFW_OPENGL_PROFILE glfw.GLFW_OPENGL_CORE_PROFILE
    # glfw.WindowHint glfw.GLFW_SAMPLES 4

    let main-window = (glfw.CreateWindow 1280 720 "untitled metroidvania" null null)
    if (main-window == null)
        assert false "Failed to create a window with specified settings."
    glfw.MakeContextCurrent main-window
    glfw.SwapInterval 1

    glfw.SetKeyCallback main-window keybind-handler

    renderer.init main-window
    sound.init;
    input.init main-window
    entity.init-archetypes;

    ig.CreateContext null
    local io = (ig.GetIO)
    ig.impl.Glfw_InitForOpenGL main-window true
    ig.impl.OpenGL3_Init null

    gizmo-shader = (renderer.GPUShaderProgram gizmo-vshader gizmo-fshader)
    debug-gizmos = ((renderer.Mesh 2DVertex u16) 128)

    start-game;

    local last-time = (glfw.GetTime)
    while (not (glfw.WindowShouldClose main-window))
        glfw.PollEvents;
        glfw.GetFramebufferSize main-window &window-width &window-height

        global dt-accum : f64
        global fps-time-accum : f64
        global fps-samples-counter : u64
        global avg-fps : f32
        let time-scale = 1
        let now = (glfw.GetTime)
        # at 15 fps the game just slows down, to avoid spiral of death / deal with sudden spikes.
        let real-dt = (min (now - last-time) (1 / 15:f64))
        last-time = now
        dt-accum += real-dt * time-scale

        if (fps-time-accum > 0.5)
            avg-fps = (1.0:f64 / (fps-time-accum / (fps-samples-counter as f64))) as f32
            fps-time-accum = 0.0
            fps-samples-counter = 0
        fps-time-accum += real-dt
        fps-samples-counter += 1

        step-size := 1 / 60

        input.update;
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

        let flags = ig.ImGuiWindowFlags
        ig.SetNextWindowPos (vec2 10 10) ig.ImGuiCond.Always (vec2 0 0)
        ig.Begin "version" null
            | flags.NoDecoration
                flags.NoBackground
        ig.Text (config.GAME_VERSION as rawstring)
        ig.End;

        if show-debug-info?
            ig.SetNextWindowPos (vec2 (window-width - 320) 5) ig.ImGuiCond.FirstUseEver (vec2 0 0)
            ig.SetNextWindowCollapsed true ig.ImGuiCond.FirstUseEver
            ig.Begin "Debug Info" null 0

            global show-entity-list? : bool
            global show-perf-stats? : bool true
            global show-gamepad-buttons? : bool

            let debug-button-size = (vec2 300 20)
            if (ig.Button "Entity List" debug-button-size)
                show-entity-list? = true
            if (ig.Button "Performance" debug-button-size)
                show-perf-stats? = (not show-perf-stats?)
            if (ig.Button "Gamepad State" debug-button-size)
                show-gamepad-buttons? = true

            global entity-list-idx : i32 -1
            if show-entity-list?
                ig.Begin "Entity List" &show-entity-list? 0
                for i ent in (enumerate current-scene.entities)
                    using import .radlib.stringtools

                    let selected? = (entity-list-idx == i)
                    if (ig.SelectableBool (format "%d %s" ent.id (tocstr ent.tag)) selected? 0 (vec2 300 20))
                        entity-list-idx = i
                        debug-selected-entity = ent.id
                    if selected?
                        ig.Begin (tocstr ent.tag) null 0
                        # position in tiles
                        vvv bind tile-p
                        do
                            let tile-dimensions =
                                vec2
                                    current-scene.tileset.tile-width
                                    current-scene.tileset.tile-height
                            let tile-p = (ivec2 (floor (player.position / tile-dimensions)))

                        ig.Text "position: %.3f %.3f (%d %d)" ent.position.x ent.position.y
                            \ tile-p.x tile-p.y
                        ig.Text "velocity: %.3f %.3f" (unpack (ent.velocity * step-size))
                        let grounded? =
                            if ('has-component? ent 'ActionPuppet)
                                let c = ('get-component ent 'ActionPuppet)
                                tocstr
                                    (c as component.components.ActionPuppet) . grounded?
                            else
                                "N/A" as rawstring
                        ig.Text "grounded?: %s" grounded?
                        for k c in ent.components
                            'display-ui c
                        ig.End;

                ig.End;

            if show-perf-stats?
                ig.SetNextWindowPos (vec2 (window-width - 100) (window-height - 100)) ig.ImGuiCond.FirstUseEver (vec2 0 0)
                ig.Begin "Performance" &show-perf-stats?
                    flags.NoTitleBar | flags.NoResize | flags.NoBackground
                ig.Text "avg fps: %.3f" avg-fps
                ig.End;

            if show-gamepad-buttons?
                ig.Begin "Gamepad State" &show-perf-stats? 0
                va-map
                    inline (button)
                        let down? = (input.down? button)
                        ig.Text "%s: %s" ((tostring button) as rawstring)
                            ? down? ("true" as rawstring) ("false" as rawstring)
                    _ 'A 'B 'Left 'Right 'Up 'Down
                ig.End;

            ig.End;

        ig.Render;
        ig.impl.OpenGL3_RenderDrawData (ig.GetDrawData)

        glfw.SwapBuffers main-window

    glfw.DestroyWindow main-window
    glfw.Terminate;

    sound.cleanup;

do
    let main
    locals;
