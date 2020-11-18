# TODO: import as submodule
using import radlib.core-extensions

using import enum
using import struct
using import glm
using import Array
using import Rc
using import Map
using import String
using import itertools

import .math

# DEPENDENCIES
# ================================================================================
switch operating-system
case 'linux
    load-library "../lib/libgame.so"
    load-library "../lib/libglfw.so"
    load-library "../lib/libphysfs.so"
case 'windows
    load-library "../lib/libgame.dll"
    load-library "../lib/glfw3.dll"
    load-library "../lib/libphysfs.dll"
default
    error "Unsupported OS."

run-stage;

let C = (import radlib.libc)

let glfw = (import .FFI.glfw)
let gl = (import .FFI.glad)
let physfs = (import .FFI.physfs)
let stbi = (import .FFI.stbi)
let cjson = (import .FFI.cjson)

# WINDOW AND OPENGL INITIALIZATION
# ================================================================================
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
glfw.WindowHint glfw.GLFW_SAMPLES 4

let main-window = (glfw.CreateWindow 1280 720 "gam??" null null)
if (main-window == null)
    error "Failed to create a window with specified settings."
glfw.MakeContextCurrent main-window
glfw.SwapInterval 1

gl.init;

let argc argv = (launch-args)
if (not (physfs.init (argv @ 0)))
    error "Failed to initialize PHYSFS."
physfs.mount "../data" "/" true
physfs.setWriteDir "."

run-stage;

enum OpenGLDebugLevel plain
    HIGH
    MEDIUM
    LOW
    NOTIFICATION

# log-level is the lowest severity level we care about.
inline make-openGL-debug-callback (log-level)
    let log-level = (log-level as i32)
    fn openGL-error-callback (source _type id severity _length message user-param)
        inline gl-debug-source (source)
            match source
            case gl.GL_DEBUG_SOURCE_API                "API"
            case gl.GL_DEBUG_SOURCE_WINDOW_SYSTEM      "Window System"
            case gl.GL_DEBUG_SOURCE_SHADER_COMPILER    "Shader Compiler"
            case gl.GL_DEBUG_SOURCE_THIRD_PARTY        "Third Party"
            case gl.GL_DEBUG_SOURCE_APPLICATION        "Application"
            case gl.GL_DEBUG_SOURCE_OTHER              "Other"
            default                                    "?"

        inline gl-debug-type (type_)
            match type_
            case gl.GL_DEBUG_TYPE_ERROR                "Error"
            case gl.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR  "Deprecated"
            case gl.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR   "Undefined Behavior"
            case gl.GL_DEBUG_TYPE_PORTABILITY          "Portability"
            case gl.GL_DEBUG_TYPE_PERFORMANCE          "Performance"
            case gl.GL_DEBUG_TYPE_OTHER                "Other"
            default                                    "?"

        inline gl-debug-severity (severity)
            match severity
            case gl.GL_DEBUG_SEVERITY_HIGH             "High"
            case gl.GL_DEBUG_SEVERITY_MEDIUM           "Medium"
            case gl.GL_DEBUG_SEVERITY_LOW              "Low"
            case gl.GL_DEBUG_SEVERITY_NOTIFICATION     "Notification"
            default                                    "?"

        using OpenGLDebugLevel
        match severity

        case gl.GL_DEBUG_SEVERITY_HIGH
        case gl.GL_DEBUG_SEVERITY_MEDIUM
            static-if (log-level < MEDIUM)
                return;
        case gl.GL_DEBUG_SEVERITY_LOW
            static-if (log-level < LOW)
                return;
        case gl.GL_DEBUG_SEVERITY_NOTIFICATION
            static-if (log-level < NOTIFICATION)
                return;
        default
            ;

        print
            "source:"
            gl-debug-source source
            "| type:"
            gl-debug-type _type
            "| severity:"
            gl-debug-severity severity
            "| message:"
            string message
        ;
gl.Enable gl.GL_DEBUG_OUTPUT
gl.Enable gl.GL_BLEND
gl.BlendFunc gl.GL_SRC_ALPHA gl.GL_ONE_MINUS_SRC_ALPHA
gl.Enable gl.GL_MULTISAMPLE
gl.Enable gl.GL_FRAMEBUFFER_SRGB
# TODO: add some colors to this
gl.DebugMessageCallback (make-openGL-debug-callback OpenGLDebugLevel.LOW) null
local VAO : gl.GLuint
gl.GenVertexArrays 1 &VAO
gl.BindVertexArray VAO

# HELPERS AND TYPES
# ================================================================================
let GPUBuffer =
    make-handle-type 'GPUBuffer u32
        inline __drop (self)
            local handle : u32 = (storagecast (view self))
            gl.DeleteBuffers 1 &handle
            ;

struct 2DVertex plain
    position : vec2
    color : vec4

fn gl-make-buffer (kind size)
    local handle : u32
    gl.GenBuffers 1 &handle
    gl.BindBuffer (kind as u32) handle
    gl.NamedBufferStorage handle (size as i64) null
        gl.GL_DYNAMIC_STORAGE_BIT
    GPUBuffer (deref handle)

typedef Mesh < Struct
    @@ memo
    inline __typecall (cls attributeT indexT)
        struct (.. "Mesh<" (tostring attributeT) "," (tostring indexT) ">")
            let IndexFormat = indexT
            let AttributeType = attributeT

            attribute-data : (Array AttributeType)
            _attribute-buffer : GPUBuffer
            _attribute-buffer-size : usize
            index-data : (Array IndexFormat)
            _index-buffer : GPUBuffer
            _index-buffer-size : usize

            fn ensure-storage (self)
                """"If store size is not enough to contain uploaded data, destroys the
                    attached GPU resources and recreates them with differently sized data stores.
                let attr-data-size = ((sizeof AttributeType) * (countof self.attribute-data))
                let index-data-size = ((sizeof IndexFormat) * (countof self.index-data))

                # find a size that can hold the amount of data we want by multiplying by 2 repeatedly
                inline find-next-size (current required)
                    if (current == 0)
                        required
                    else
                        loop (size = (deref current))
                            assert (size >= current) # probably an overflow
                            if (size >= required)
                                break size
                            size * 2

                if (self._attribute-buffer-size < attr-data-size)
                    let new-size = (find-next-size self._attribute-buffer-size attr-data-size)
                    self._attribute-buffer =
                        gl-make-buffer gl.GL_SHADER_STORAGE_BUFFER new-size
                    self._attribute-buffer-size = new-size
                if (self._index-buffer-size < index-data-size)
                    let new-size = (find-next-size self._index-buffer-size index-data-size)
                    self._index-buffer =
                        gl-make-buffer gl.GL_ELEMENT_ARRAY_BUFFER new-size
                    self._index-buffer-size = new-size
                ;

            fn update (self)
                """"Uploads mesh data to GPU.
                'ensure-storage self
                gl.NamedBufferSubData self._attribute-buffer 0 (self._attribute-buffer-size as i64)
                    (imply self.attribute-data pointer) as voidstar
                gl.NamedBufferSubData self._index-buffer 0 (self._index-buffer-size as i64)
                    (imply self.index-data pointer) as voidstar

            inline __typecall (cls expected-attr-count)
                let expected-index-count = ((expected-attr-count * 1.5) as usize) # estimate
                let attr-store-size = ((sizeof AttributeType) * expected-attr-count)
                let ibuffer-store-size = ((sizeof IndexFormat) * expected-index-count)

                let attr-handle =
                    gl-make-buffer gl.GL_SHADER_STORAGE_BUFFER attr-store-size

                let ibuffer-handle =
                    gl-make-buffer gl.GL_ELEMENT_ARRAY_BUFFER ibuffer-store-size

                local attr-array : (Array AttributeType)
                'reserve attr-array expected-attr-count
                local index-array : (Array IndexFormat)
                'reserve index-array expected-index-count

                super-type.__typecall cls
                    attribute-data = attr-array
                    _attribute-buffer = attr-handle
                    _attribute-buffer-size = attr-store-size
                    index-data = index-array
                    _index-buffer = ibuffer-handle
                    _index-buffer-size = ibuffer-store-size

fn load-full-file (filename)
    let file = (physfs.openRead filename)
    if (file == null)
        hide-traceback;
        error (.. "could not open file " (string filename))

    let size = (physfs.fileLength file)
    local data : (Array i8)
    'resize data size
    let read = (physfs.readBytes file data (size as u64))
    assert (read == size)

    data

fn load-image-data (filename)
    let data = (load-full-file filename)
    local x : i32
    local y : i32
    local n : i32
    let img-data =
        stbi.load_from_memory
            (imply data pointer) as (pointer u8)
            (countof data) as i32
            &x
            &y
            &n
            0
    let data-len = (x * y * n)
    _
        Struct.__typecall (Array u8)
            _items = img-data
            _count = data-len
            _capacity = data-len
        deref x
        deref y
        deref n

struct ImageData
    data : (Array u8)
    width : usize
    height : usize
    channels : u32

    inline __typecall (cls filename)
        let data w h c = (load-image-data filename)
        super-type.__typecall cls
            data = data
            width = (w as usize)
            height = (h as usize)
            channels = (c as u32)

let GPUTexture =
    make-handle-type 'GPUTexture u32
        inline __drop (self)
            gl.DeleteTextures 1 (&local (storagecast (view self)))

struct ArrayTexture2D
    _handle : GPUTexture

    inline __typecall (cls filename layer-width layer-height)
        let img-data = (ImageData filename)
        local handle : u32
        # TODO: accomodate more mip levels
        let mip-count = 1
        subimg-columns := img-data.width // layer-width
        subimg-rows := img-data.height // layer-height
        layer-count := subimg-rows * subimg-columns
        gl.GenTextures 1 &handle
        gl.BindTexture gl.GL_TEXTURE_2D_ARRAY handle
        gl.TexStorage3D gl.GL_TEXTURE_2D_ARRAY mip-count
            gl.GL_SRGB8_ALPHA8
            layer-width as i32
            layer-height as i32
            layer-count as i32
        gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH (img-data.width as i32)
        for i in (range layer-count)
            let subimg-col subimg-row =
                i % subimg-columns
                i // subimg-columns
            let first-texel =
                +
                    layer-width * layer-height * subimg-columns * subimg-row
                    layer-width * subimg-col

            gl.TextureSubImage3D
                handle
                0 # mip level
                0 # xoffset
                0 # yoffset
                i as i32 # zoffset
                layer-width as i32
                layer-height as i32
                1 # depth
                gl.GL_RGBA
                gl.GL_UNSIGNED_BYTE
                & (img-data.data @ (first-texel * img-data.channels))
        gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH 0
        gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_MIN_FILTER gl.GL_NEAREST
        gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_MAG_FILTER gl.GL_NEAREST
        gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_WRAP_S gl.GL_CLAMP_TO_EDGE
        gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_WRAP_T gl.GL_CLAMP_TO_EDGE

        super-type.__typecall cls
            _handle = (GPUTexture handle)

struct Sprite plain
    position : vec2
    scale : vec2
    pivot : vec2
    layer : u32
    rotation : f32

struct SpriteBatch
    sprites : (Mesh Sprite u16)
    image : ArrayTexture2D
    _dirty? : bool

    inline __typecall (cls image-filename layer-width layer-height)
        super-type.__typecall cls
            sprites = ((Mesh Sprite u16) 128)
            image = (ArrayTexture2D image-filename layer-width layer-height)
            _dirty? = false

    fn add (self sprite)
        self._dirty? = true
        local indices =
            arrayof u16 0 2 3 3 1 0
        idx-offset := (countof self.sprites.attribute-data) * 4
        for idx in indices
            'append self.sprites.index-data ((idx-offset + idx) as u16)
        'append self.sprites.attribute-data sprite
        # return sprite index
        (countof self.sprites.attribute-data) - 1

    fn draw (self)
        if self._dirty?
            'update self.sprites
            self._dirty? = false
        gl.BindBufferBase gl.GL_SHADER_STORAGE_BUFFER 0 self.sprites._attribute-buffer
        gl.BindBuffer gl.GL_ELEMENT_ARRAY_BUFFER self.sprites._index-buffer
        gl.BindTextures 0 1
            &local (storagecast (view self.image._handle))
        gl.DrawElements gl.GL_TRIANGLES ((countof self.sprites.index-data) as i32)
           \ gl.GL_UNSIGNED_SHORT null

fn gl-compile-shader (source kind)
    imply kind i32
    source as:= rawstring

    let handle = (gl.CreateShader (kind as u32))
    gl.ShaderSource handle 1 (&local source) null
    gl.CompileShader handle

    local compilation-status : i32
    gl.GetShaderiv handle gl.GL_COMPILE_STATUS &compilation-status
    if (not compilation-status)
        local log-length : i32
        local message : (array i8 1024)
        gl.GetShaderInfoLog handle (sizeof message) &log-length &message
        print (default-styler 'style-error "Shader compilation error:")
        print (string &message (log-length as usize))
    handle

fn gl-link-shader-program (vs fs)
    let program = (gl.CreateProgram)
    gl.AttachShader program vs
    gl.AttachShader program fs
    gl.LinkProgram program
    # could make this less copy pastey by abstracting away error logging
    local link-status : i32
    gl.GetProgramiv program gl.GL_LINK_STATUS &link-status
    if (not link-status)
        local log-length : i32
        local message : (array i8 1024)
        gl.GetProgramInfoLog program (sizeof message) &log-length &message
        print (default-styler 'style-error "Shader program linking error:")
        print (string &message (log-length as usize))
    # because we preemptively delete the shader stages, they are
        already marked for deletion when the program is dropped.
    gl.DeleteShader fs
    gl.DeleteShader vs
    program

let GPUShaderProgram =
    make-handle-type 'GPUShaderProgram u32
        inline __drop (self)
            gl.DeleteProgram (storagecast (view self))

struct ShaderProgram
    _handle : GPUShaderProgram
    inline __typecall (cls vs fs)
        let vertex-module =
            gl-compile-shader
                static-compile-glsl 450 'vertex (static-typify vs)
                gl.GL_VERTEX_SHADER
        let fragment-module =
            gl-compile-shader
                static-compile-glsl 450 'fragment (static-typify fs)
                gl.GL_FRAGMENT_SHADER

        let program = (gl-link-shader-program vertex-module fragment-module)
        super-type.__typecall cls
            _handle = (bitcast program GPUShaderProgram)

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
            let data = (load-full-file filename)
            # FIXME: this version doesn't seem to have ParseWithLength, but it would be preferrable.
            # change it once we use our in-tree version of cJSON.
            let json-data = (cjson.Parse data)
            let image-name =
                cjson.GetStringValue
                    cjson.GetObjectItem json-data "image"
            let tile-width =
                deref
                    (cjson.GetObjectItem json-data "tilewidth") . valueint
            let tile-height =
                deref
                    (cjson.GetObjectItem json-data "tileheight") . valueint

            using import radlib.libc
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

global player-sprite : (mutable pointer Sprite)

struct Scene
    tileset : (Rc Tileset)
    width : u32
    height : u32
    level-data : (Array u32)
    draw-data : SpriteBatch

    inline __typecall (cls filename)
        fn load-tiled-level (filename)
            let tiled-scene = (cjson.Parse (load-full-file filename))

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
            let tile-array = (cjson.GetObjectItem level-layer "data")
            for tile in (json-array->generator tile-array)
                'append level-data (tile.valueint as u32)
            local level-sprites =
                SpriteBatch tileset-obj.image tileset-obj.tile-width tileset-obj.tile-height
            for i x y in (enumerate (dim scene-width-tiles scene-height-tiles))
                let tile = (level-data @ i)
                let scale =
                    if (tile == 0)
                        vec2; # invisible tile
                    else
                        vec2 1
                'add level-sprites
                    Sprite
                        position =
                            vec2
                                tileset-obj.tile-width * (x as u32)
                                # because images go y down but we go y up
                                tileset-obj.tile-height * ((scene-height-tiles - 1 - y) as u32)
                        scale = scale
                        pivot = (vec2)
                        layer = (tile - 1)
                        rotation = 0

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
                    (scene-height-px as i32) - (y as i32)
            let sprite-index =
                'add level-sprites
                    Sprite
                        position = (tiled->worldpos px py)
                        scale = (vec2 1)
                        layer = 23 # red little man

            player-sprite = (& (level-sprites.sprites.attribute-data @ sprite-index))
            # end of the hack

            cjson.Delete tiled-scene

            super-type.__typecall cls
                tileset = tileset-obj
                draw-data = level-sprites
                width = scene-width-px
                height = scene-height-px
                level-data = level-data
        load-tiled-level filename

struct Camera plain
    position : vec2
    scale : vec2
    viewport : vec2
    bounds : vec4

    fn world->screen (self world)
        world - self.position

    fn follow (self target)
        let target = (world->screen self target)
        # define focus box
        center := self.viewport / 2
        let focus-box-size = (vec2 96 48)
        f0 := center - (focus-box-size / 2)
        f1 := f0 + focus-box-size

        let px py = self.position.x self.position.y
        let new-px =
            if (target.x < f0.x)
                px - (f0.x - target.x)
            elseif (target.x > f1.x)
                px - (f1.x - target.x)
            else
                deref px
        let new-py =
            if (target.y < f0.y)
                py - (f0.y - target.y)
            elseif (target.y > f1.y)
                py - (f1.y - target.y)
            else
                deref py

        self.position =
            # max has to be adjusted because position is at top left corner of viewport
            vec2
                clamp new-px self.bounds.s (self.bounds.p - self.viewport.x)
                clamp new-py self.bounds.t (self.bounds.q - self.viewport.y)
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
            gl.GetUniformLocation shader._handle "transform"
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

    local texcoords =
        arrayof vec2
            vec2 0 1
            vec2 1 1
            vec2 0 0
            vec2 1 0

    let sprites = attributes.data
    idx         := gl_VertexID
    sprite      := sprites @ (idx // 4)
    origin      := sprite.position
    vertex      := vertices @ (idx % 4)
    orientation := sprite.rotation
    pivot       := sprite.pivot
    scale       := sprite.scale

    # TODO: explain what this does in a comment
    gl_Position =
        * transform
            vec4 (origin + pivot + (math.rotate ((vertex * layer_size * scale) - pivot) orientation)) 0 1
    vtexcoord = (vec3 (texcoords @ (idx % 4)) sprite.layer)

fn sprite-fragment-shader ()
    using import glsl
    in vtexcoord : vec3
        location = 2
    out fcolor : vec4
        location = 0

    uniform sprite-tex : sampler2DArray
    fcolor = (texture sprite-tex vtexcoord)

global sprite-shader = (ShaderProgram sprite-vertex-shader sprite-fragment-shader)
gl.UseProgram sprite-shader._handle

global level1 = (Scene "levels/1.json")
global main-camera : Camera
    position = (vec2)
    scale = (vec2 6)

fn key-down? (code)
    (glfw.GetKey main-window code) as bool

# GAME CODE
# ================================================================================
glfw.SetKeyCallback main-window
    fn (window _key scancode action mods)
        if ((_key == glfw.GLFW_KEY_ESCAPE) and (action == glfw.GLFW_RELEASE))
            glfw.SetWindowShouldClose main-window true
        ;

global window-width : i32
global window-height : i32

# NOTE: very temporary thing, that's why it's down here.
global player :
    struct Player plain
        position : vec2

player.position = player-sprite.position
main-camera.bounds =
    vec4 0 0 level1.width level1.height

fn update (dt)
    # TODO: create a follow function
    let player-speed = 40
    if (key-down? glfw.GLFW_KEY_LEFT)
        player.position -= (vec2 player-speed 0) * dt
    if (key-down? glfw.GLFW_KEY_RIGHT)
        player.position += (vec2 player-speed 0) * dt
    if (key-down? glfw.GLFW_KEY_UP)
        player.position += (vec2 0 player-speed) * dt
    if (key-down? glfw.GLFW_KEY_DOWN)
        player.position -= (vec2 0 player-speed) * dt

    player-sprite.position = (floor player.position)

    main-camera.viewport = ((vec2 window-width window-height) / main-camera.scale)
    'follow main-camera player-sprite.position

fn draw ()
    gl.ClearColor 1.0 0.2 0.2 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    gl.Uniform2f
        gl.GetUniformLocation sprite-shader._handle "layer_size"
        level1.tileset.tile-width as f32
        level1.tileset.tile-height as f32

    'apply main-camera sprite-shader
    'update level1.draw-data.sprites
    'draw level1.draw-data

global last-time = (glfw.GetTime)
while (not (glfw.WindowShouldClose main-window))
    glfw.PollEvents;
    glfw.GetFramebufferSize main-window &window-width &window-height
    gl.Viewport 0 0 window-width window-height

    # TODO: FBO to provide an internal resolution independent from
    # display will go here.

    global dt-accum : f64

    let now = (glfw.GetTime)
    let real-dt = (now - last-time)
    last-time = now
    dt-accum += real-dt

    step-size := 1 / 60

    while (dt-accum >= step-size)
        dt-accum -= step-size
        update step-size

    draw;

    glfw.SwapBuffers main-window

# CLEANUP
# ================================================================================
glfw.DestroyWindow main-window
glfw.Terminate;
