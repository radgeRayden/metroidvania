using import enum
let gl = (import foreign.gl)

fn init ()
    gl.init!;
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
    # TODO: add some colors to this
    gl.DebugMessageCallback (make-openGL-debug-callback OpenGLDebugLevel.LOW) null
    local VAO : gl.GLuint
    gl.GenVertexArrays 1 &VAO
    gl.BindVertexArray VAO

    ;

fn begin-frame (clear-color)
    gl.ClearColor (unpack clear-color)
    gl.Clear (gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)
    ;

fn end-frame ()
    ;

do
    let
        init
        begin-frame
        end-frame
    locals;
