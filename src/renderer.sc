using import .radlib.core-extensions
using import enum
using import struct
using import Array
using import String
using import Rc
using import Map
using import glm

let gl = (import .FFI.glad)
let cjson = (import .FFI.cjson)
let glfw = (import .FFI.glfw)
let C = (import .radlib.libc)

import .filesystem
using import .common
using constants # common.constants
import .math

# LOW LEVEL BASE
# ================================================================================
fn init-gl ()
    gl.init;

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
    # gl.Enable gl.GL_MULTISAMPLE
    gl.Enable gl.GL_FRAMEBUFFER_SRGB
    # TODO: add some colors to this
    gl.DebugMessageCallback (make-openGL-debug-callback OpenGLDebugLevel.LOW) null
    local VAO : gl.GLuint
    gl.GenVertexArrays 1 &VAO
    gl.BindVertexArray VAO

typedef GPUBuffer <:: u32
    inline __typecall (cls kind size)
        local handle : u32
        gl.GenBuffers 1 &handle
        gl.BindBuffer (kind as u32) handle
        gl.NamedBufferStorage handle (size as i64) null
            gl.GL_DYNAMIC_STORAGE_BIT
        bitcast handle this-type

    inline __drop (self)
        local handle : u32 = (storagecast (view self))
        gl.DeleteBuffers 1 &handle
        ;

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
                        GPUBuffer gl.GL_SHADER_STORAGE_BUFFER new-size
                    self._attribute-buffer-size = new-size
                if (self._index-buffer-size < index-data-size)
                    let new-size = (find-next-size self._index-buffer-size index-data-size)
                    self._index-buffer =
                        GPUBuffer gl.GL_ELEMENT_ARRAY_BUFFER new-size
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
                    GPUBuffer gl.GL_SHADER_STORAGE_BUFFER attr-store-size

                let ibuffer-handle =
                    GPUBuffer gl.GL_ELEMENT_ARRAY_BUFFER ibuffer-store-size

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

typedef GPUTexture <:: u32
    inline __typecall (cls handle)
        bitcast handle this-type

    inline __drop (self)
        gl.DeleteTextures 1 (&local (storagecast (view self)))

struct ArrayTexture2D
    _handle : GPUTexture
    _layer-width : u32
    _layer-height : u32
    _layer-count : u32

    # NOTE: the layer count is inferred.
    inline... __typecall (cls, filenames : (Array String), layer-width, layer-height)
        assert ((countof filenames) > 0)
        local handle : u32
        # TODO: accomodate more mip levels
        let mip-count = 1
        # NOTE: to deduce the layer count, it's easier to load all images in memory at once.
        local images : (Array ImageData)
        for file in filenames
            'append images (ImageData file)

        let layer-count =
            fold (layer-count = 0:usize) for img-data in images
                assert (((img-data.width % layer-width) == 0) and ((img-data.height % layer-height) == 0))
                    "source image size wasn't a multiple of the requested layer size"

                subimg-columns := img-data.width // layer-width
                subimg-rows := img-data.height // layer-height
                layer-count + (subimg-rows * subimg-columns)

        gl.GenTextures 1 &handle
        gl.BindTexture gl.GL_TEXTURE_2D_ARRAY handle
        gl.TexStorage3D gl.GL_TEXTURE_2D_ARRAY mip-count
            gl.GL_SRGB8_ALPHA8
            layer-width as i32
            layer-height as i32
            layer-count as i32

        for img-data in images
            subimg-columns    := img-data.width // layer-width
            subimg-rows       := img-data.height // layer-height
            local-layer-count := subimg-rows * subimg-columns

            gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH (img-data.width as i32)
            for i in (range local-layer-count)
                let col row =
                    i % subimg-columns
                    i // subimg-columns
                let first-texel =
                    +
                        layer-width * layer-height * subimg-columns * row
                        layer-width * col

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
            gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_MIN_FILTER gl.GL_NEAREST
            gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_MAG_FILTER gl.GL_NEAREST
            gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_WRAP_S gl.GL_CLAMP_TO_EDGE
            gl.TexParameteri gl.GL_TEXTURE_2D_ARRAY gl.GL_TEXTURE_WRAP_T gl.GL_CLAMP_TO_EDGE

        gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH 0

        super-type.__typecall cls
            _handle = (GPUTexture handle)
            _layer-width = layer-width
            _layer-height = layer-height
            _layer-count = (layer-count as u32)

    case (cls, filename : String, layer-width, layer-height)
        local filenames : (Array String)
        'append filenames (copy filename)
        this-function cls filenames layer-width layer-height

struct SpriteBatch
    sprites : (Mesh Sprite u16)
    image : (Rc ArrayTexture2D)
    _dirty? : bool

    inline... __typecall (cls, image : (Rc ArrayTexture2D))
        super-type.__typecall cls
            sprites = ((Mesh Sprite u16) 128)
            image = image
            _dirty? = false
    case (cls image-filename layer-width layer-height)
        this-function cls
            Rc.wrap (ArrayTexture2D image-filename layer-width layer-height)
    case (cls image-filename)
        this-function cls
            Rc.wrap (ArrayTexture2D image-filename (unpack ATLAS_PAGE_SIZE))

    fn add (self sprite)
        let sprites = self.sprites

        self._dirty? = true
        local indices =
            arrayof u16 0 2 3 3 1 0
        idx-offset := (countof sprites.attribute-data) * 4
        for idx in indices
            'append sprites.index-data ((idx-offset + idx) as u16)
        'append sprites.attribute-data sprite
        # return sprite index
        (countof sprites.attribute-data) - 1

    fn clear (self)
        'clear self.sprites.attribute-data
        'clear self.sprites.index-data

    fn draw (self)
        if self._dirty?
            'update self.sprites
            self._dirty? = false
        let attribute-buffer index-buffer index-count =
            _
                self.sprites._attribute-buffer
                self.sprites._index-buffer
                countof self.sprites.index-data

        gl.BindBufferBase gl.GL_SHADER_STORAGE_BUFFER 0 attribute-buffer
        gl.BindBuffer gl.GL_ELEMENT_ARRAY_BUFFER index-buffer
        gl.BindTextures 0 1
            &local (storagecast (view self.image._handle))
        gl.DrawElements gl.GL_TRIANGLES (index-count as i32)
            \ gl.GL_UNSIGNED_SHORT null
        ;

typedef GPUShaderProgram <:: u32
    fn compile-shader (source kind)
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

    fn link-program (vs fs)
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

    inline... __typecall (cls vs fs)
        let vertex-module =
            compile-shader
                static-compile-glsl 450 'vertex (static-typify vs)
                gl.GL_VERTEX_SHADER
        let fragment-module =
            compile-shader
                static-compile-glsl 450 'fragment (static-typify fs)
                gl.GL_FRAGMENT_SHADER

        let program = (link-program vertex-module fragment-module)
        bitcast program this-type
    case (cls handle)
        bitcast handle this-type

    inline __imply (selfT otherT)
        static-if (otherT == (storageof this-type))
            inline (self)
                storagecast (view self)

    inline __drop (self)
        gl.DeleteProgram (storagecast (view self))

# SHADER DEFINITIONS
# ================================================================================
fn sprite-vertex-shader ()
    using import glsl
    buffer attributes :
        struct AttributeArray plain
            data : (array Sprite)

    uniform transform : mat4

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
            vec4 (origin + pivot + (math.rotate ((vertex * scale) - pivot) orientation)) 0 1
    vtexcoord = (vec3 (texcoords @ (idx % 4)) sprite.page)

fn sprite-fragment-shader ()
    using import glsl
    in vtexcoord : vec3
        location = 2
    out fcolor : vec4
        location = 0

    uniform sprite-tex : sampler2DArray
    fcolor = (texture sprite-tex vtexcoord)

# INTERNAL MODULE STATE
# ================================================================================
global sprite-metadata : (Map String (Array Sprite))
global sprite-layers : (Array SpriteBatch 4)

global main-render-target : u32
global fb-color-attachment : GPUTexture 0
global fb-depth-attachment : GPUTexture 0

global main-window : (mutable@ glfw.window)

global game-shader : GPUShaderProgram 0
global world-transform : mat4

# INTERFACE
# ================================================================================
fn init (window)
    # TODO: this won't be necessary once windowing code
    # is moved to a dedicated module.
    main-window = window
    init-gl;

    local atlases : (Array String)
    for name in (filesystem.get-directory-files "sprites")
        let match? start end = ('match? "atlas[0-9]+.png" (name as string))
        if match?
            'append atlases ("sprites/" .. name)

    # NOTE: for a game this size, I opted to load all the sprite textures
    # at once, in a single ArrayTexture where they can be indexed by position in
    # the atlas and page (array layer).
    let mega-atlas =
        Rc.wrap (ArrayTexture2D atlases ATLAS_PAGE_SIZE.x ATLAS_PAGE_SIZE.y)
    for i in (range ('capacity sprite-layers))
        'append sprite-layers
            SpriteBatch (copy mega-atlas)

    let metadata-json =
        do
            let file = (filesystem.load-full-file "sprites/metadata.json")
            cjson.ParseWithLength file (countof file)

    let sprite-groups = (cjson.GetObjectItem metadata-json "groups")

    for group in sprite-groups
        let group-name =
            do
                let rawstr = (cjson.GetStringValue (cjson.GetObjectItem group "name"))
                String rawstr (C.string.strlen rawstr)

        local sprites : (Array Sprite)
        let images = (cjson.GetObjectItem group "images")
        for image in images
            let page x y width height =
                (cjson.GetObjectItem image "page") . valueint
                (cjson.GetObjectItem image "x") . valueint
                (cjson.GetObjectItem image "y") . valueint
                (cjson.GetObjectItem image "width") . valueint
                (cjson.GetObjectItem image "height") . valueint
            'append sprites
                Sprite
                    scale = (vec2 width height)
                    page = (page as u32)
                    texcoords =
                        vec4
                            x / ATLAS_PAGE_SIZE.x
                            y / ATLAS_PAGE_SIZE.y
                            (x + width) / ATLAS_PAGE_SIZE.x
                            (y + height) / ATLAS_PAGE_SIZE.y

        'set sprite-metadata group-name (deref sprites)

    cjson.Delete metadata-json

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

    game-shader = (GPUShaderProgram sprite-vertex-shader sprite-fragment-shader)
    gl.UseProgram game-shader

fn begin-frame ()
    gl.BindFramebuffer gl.GL_FRAMEBUFFER main-render-target
    gl.Viewport 0 0 INTERNAL_RESOLUTION.x INTERNAL_RESOLUTION.y
    gl.ClearColor 1.0 0.2 0.2 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    for layer in sprite-layers
        'clear layer


fn end-frame ()
    for layer in sprite-layers
        'update layer.sprites
        'draw layer

    gl.BindFramebuffer gl.GL_FRAMEBUFFER 0
    gl.ClearColor 0.005 0.005 0.005 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    # preserve aspect ratio
    local window-width : i32
    local window-height : i32
    glfw.GetFramebufferSize main-window &window-width &window-height

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

fn set-world-transform (transform)
    gl.UniformMatrix4fv
        gl.GetUniformLocation game-shader "transform"
        1
        false
        (&local transform) as (pointer f32)

do
    let
        GPUBuffer
        Mesh
        ArrayTexture2D
        SpriteBatch
        GPUShaderProgram
        GPUTexture

        sprite-layers
        sprite-metadata

        init
        begin-frame
        end-frame
        set-world-transform
    locals;
