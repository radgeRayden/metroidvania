# TODO: import as submodule
using import radlib.core-extensions

using import enum
using import struct
using import glm
using import Array

# DEPENDENCIES
# ================================================================================
load-library "../lib/libgame.so"
load-library "../lib/libglfw.so"
load-library "../lib/libphysfs.so"
run-stage;

let glfw = (import .FFI.glfw)
let gl = (import .FFI.glad)
let physfs = (import .FFI.physfs)
let stbi = (import .FFI.stbi)

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
glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MINOR 4
glfw.WindowHint glfw.GLFW_OPENGL_DEBUG_CONTEXT true
glfw.WindowHint glfw.GLFW_OPENGL_PROFILE glfw.GLFW_OPENGL_CORE_PROFILE
glfw.WindowHint glfw.GLFW_SAMPLES 4

let main-window = (glfw.CreateWindow 1280 720 "gam??" null null)
if (main-window == null)
    error "Failed to create a window with specified settings."
glfw.MakeContextCurrent main-window

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
        error (.. "could not open file " filename)

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

struct ArrayTexture2D
    _handle : u32

    inline __typecall (cls filename layer-width layer-height)
        let img-data = (ImageData filename)
        local handle : u32
        # TODO: accomodate more mip levels
        let mip-count = 1
        subimg-rows := img-data.width // layer-width
        subimg-columns := img-data.height // layer-height
        layer-count := subimg-rows * subimg-columns
        gl.GenTextures 1 &handle
        gl.BindTexture gl.GL_TEXTURE_2D_ARRAY handle
        gl.TexStorage3D gl.GL_TEXTURE_2D_ARRAY mip-count
            gl.GL_RGBA8
            layer-width
            layer-height
            layer-count as i32
        gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH (img-data.width as i32)
        for i in (range layer-count)
            let subimg-col subimg-row =
                i % subimg-columns
                i // subimg-rows
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
                layer-width
                layer-height
                1 # depth
                gl.GL_RGBA
                gl.GL_UNSIGNED_BYTE
                & (img-data.data @ (first-texel * img-data.channels))
        gl.PixelStorei gl.GL_UNPACK_ROW_LENGTH 0

        super-type.__typecall cls
            _handle = handle

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

let GPUShaderProgram =
    make-handle-type 'GPUShaderProgram u32
        inline __drop (self)
            gl.DeleteProgram (storagecast (view self))

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
    bitcast program GPUShaderProgram

# RESOURCE INITIALIZATION
# ================================================================================
let 2DMesh = (Mesh 2DVertex u16)
print 2DMesh
local sprites = (2DMesh 3000)
do
    local vertices =
        arrayof vec2
            vec2 -0.5 -0.5
            vec2 0.5 -0.5
            vec2 0.0 0.5
    local colors =
        arrayof vec4
            vec4 1.0 0 0 1
            vec4 0 1.0 0 1
            vec4 0 0 1.0 1

    for i in (range 3)
        'emplace-append sprites.attribute-data
            position = (vertices @ i)
            color = (colors @ i)
        'append sprites.index-data (i as u16)
    'update sprites

fn vertex-shader ()
    using import glsl
    buffer attributes :
        struct VertexAttributeArray plain
            data : (array 2DVertex)

    out vcolor : vec4
        location = 1

    local attr = (attributes.data @ gl_VertexID)
    gl_Position = (vec4 attr.position 0 1)
    vcolor = attr.color

fn fragment-shader ()
    using import glsl
    in vcolor : vec4
        location = 1
    out fcolor : vec4
        location = 0

    fcolor = vcolor

let vertex-module =
    gl-compile-shader
        static-compile-glsl 450 'vertex (static-typify vertex-shader)
        gl.GL_VERTEX_SHADER
let fragment-module =
    gl-compile-shader
        static-compile-glsl 450 'fragment (static-typify fragment-shader)
        gl.GL_FRAGMENT_SHADER

let default-shader = (gl-link-shader-program vertex-module fragment-module)
gl.UseProgram default-shader

# GAME LOOP
# ================================================================================
while (not (glfw.WindowShouldClose main-window))
    glfw.PollEvents;
    local width : i32
    local height : i32
    glfw.GetWindowSize main-window &width &height
    gl.Viewport 0 0 width height

    gl.ClearColor 1.0 0.2 0.2 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)

    gl.BindBufferBase gl.GL_SHADER_STORAGE_BUFFER 0 sprites._attribute-buffer
    gl.DrawArrays gl.GL_TRIANGLES 0 3

    glfw.SwapBuffers main-window

# CLEANUP
# ================================================================================
glfw.DestroyWindow main-window
# glfw.Terminate;
