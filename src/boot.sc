using import enum
using import struct
using import glm
using import Array

# DEPENDENCIES
# ================================================================================
load-library "../lib/libgame.so"
load-library "../lib/libglfw.so"
run-stage;

let glfw = (import .FFI.glfw)
let gl = (import .FFI.glad)

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
struct VertexAttributes plain
    position : vec2

struct Mesh
    attribute-data : (Array VertexAttributes)
    _attribute-buffer : u32
    _attribute-buffer-size : usize
    index-data : (Array u16)
    _index-buffer : u32
    _index-buffer-size : usize

    fn resize (self)
        ;

    inline __typecall (cls expected-vertices)
        let expected-index-count = ((expected-vertices * 1.5) as usize) # estimate
        let attr-store-size = ((sizeof VertexAttributes) * expected-vertices)
        let ibuffer-store-size = ((sizeof u16) * expected-index-count)

        local attr-handle : u32
        gl.GenBuffers 1 &attr-handle
        gl.BindBuffer gl.GL_SHADER_STORAGE_BUFFER attr-handle
        gl.NamedBufferStorage attr-handle attr-store-size null
            gl.GL_DYNAMIC_STORAGE_BIT

        local ibuffer-handle : u32
        gl.GenBuffers 1 &ibuffer-handle
        gl.BindBuffer gl.GL_ELEMENT_ARRAY_BUFFER ibuffer-handle
        gl.NamedBufferStorage ibuffer-handle
            ibuffer-store-size
            null
            gl.GL_DYNAMIC_STORAGE_BIT

        local attr-array : (Array VertexAttributes)
        'resize attr-array expected-vertices
        local index-array : (Array u16)
        'resize index-array expected-index-count

        super-type.__typecall cls
            attribute-data = attr-array
            _attribute-buffer = attr-handle
            _attribute-buffer-size = attr-store-size
            index-data = index-array
            _index-buffer = ibuffer-handle
            _index-buffer-size = ibuffer-store-size

# GAME LOOP
# ================================================================================
while (not (glfw.WindowShouldClose main-window))
    glfw.PollEvents;
    gl.ClearColor 1.0 0.2 0.2 1.0
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)
    glfw.SwapBuffers main-window

# CLEANUP
# ================================================================================
glfw.DestroyWindow main-window
glfw.Terminate;
