using import enum
using import struct

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
