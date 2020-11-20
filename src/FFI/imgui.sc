using import radlib.core-extensions
using import radlib.foreign

define-scope cimgui
    let header =
        include "../../3rd-party/cimgui/cimgui.h"
            options
                "-DCIMGUI_DEFINE_ENUMS_AND_STRUCTS"
    using header.extern filter "^(ig|ImGui_)"
    using header.typedef filter "^Im"
    using header.define
    using header.enum
    using header.struct

    define-scope impl
        # // Backend API
        # IMGUI_IMPL_API bool     ImGui_ImplOpenGL3_Init(const char* glsl_version = NULL);
        # IMGUI_IMPL_API void     ImGui_ImplOpenGL3_Shutdown();
        # IMGUI_IMPL_API void     ImGui_ImplOpenGL3_NewFrame();
        # IMGUI_IMPL_API void     ImGui_ImplOpenGL3_RenderDrawData(ImDrawData* draw_data);

        # // (Optional) Called by Init/NewFrame/Shutdown
        # IMGUI_IMPL_API bool     ImGui_ImplOpenGL3_CreateFontsTexture();
        # IMGUI_IMPL_API void     ImGui_ImplOpenGL3_DestroyFontsTexture();
        # IMGUI_IMPL_API bool     ImGui_ImplOpenGL3_CreateDeviceObjects();
        # IMGUI_IMPL_API void     ImGui_ImplOpenGL3_DestroyDeviceObjects();
        let gl3-init =
            extern 'ImGui_ImplOpenGL3_Init (function bool rawstring)
        let gl3-shutdown =
            extern 'ImGui_ImplOpenGL3_Shutdown (function void)
        let gl3-new-frame =
            extern 'ImGui_ImplOpenGL3_NewFrame (function void)
        let gl3-render-draw-data =
            extern 'ImGui_ImplOpenGL3_RenderDrawData (function void (pointer header.typedef.ImDrawData))
        let gl3-create-fonts-texture =
            extern 'ImGui_ImplOpenGL3_CreateFontsTexture   (function bool)
        let gl3-destroy-fonts-texture =
            extern 'ImGui_ImplOpenGL3_DestroyFontsTexture  (function void)
        let gl3-create-device-objects =
            extern 'ImGui_ImplOpenGL3_CreateDeviceObjects  (function bool)
        let gl3-destroy-device-objects =
            extern 'ImGui_ImplOpenGL3_DestroyDeviceObjects (function void)

        # IMGUI_IMPL_API bool     ImGui_ImplGlfw_InitForOpenGL(GLFWwindow* window, bool install_callbacks);
        # IMGUI_IMPL_API bool     ImGui_ImplGlfw_InitForVulkan(GLFWwindow* window, bool install_callbacks);
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_Shutdown();
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_NewFrame();

        # // GLFW callbacks
        # // - When calling Init with 'install_callbacks=true': GLFW callbacks will be installed for you. They will call user's previously installed callbacks, if any.
        # // - When calling Init with 'install_callbacks=false': GLFW callbacks won't be installed. You will need to call those function yourself from your own GLFW callbacks.
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_MouseButtonCallback(GLFWwindow* window, int button, int action, int mods);
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_ScrollCallback(GLFWwindow* window, double xoffset, double yoffset);
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_KeyCallback(GLFWwindow* window, int key, int scancode, int action, int mods);
        # IMGUI_IMPL_API void     ImGui_ImplGlfw_CharCallback(GLFWwindow* window, unsigned int c);

        import .glfw
        let glfw-init-for-gl =
            extern 'ImGui_ImplGlfw_InitForOpenGL (function bool (pointer glfw.window) bool)
        let glfw-shutdown =
            extern 'ImGui_ImplGlfw_Shutdown      (function void)
        let glfw-new-frame =
            extern 'ImGui_ImplGlfw_NewFrame      (function void)

sanitize-scope cimgui "^ig" "^Im"
