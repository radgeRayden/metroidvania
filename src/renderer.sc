using import .radlib.core-extensions
using import enum
using import struct
using import Array

let gl = (import .FFI.glad)

fn init ()
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

do
    let init GPUBuffer Mesh
    locals;
