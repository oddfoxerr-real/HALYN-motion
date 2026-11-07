#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <android/native_window_jni.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>
#include <cstring>

namespace {
constexpr const char* TAG = "HALYN_VULKAN";
void warn(const char* message, VkResult result = VK_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "%s (%d)", message, static_cast<int>(result));
}
bool ok(VkResult r) { return r == VK_SUCCESS; }

struct Push {
    float frame[4];  // time, scene, quality, energy
    float color[4];  // accent RGB, surface depth
    float motion[4]; // parallax XY, reflectivity, unused
    float res[4];    // width, height, unused
};

std::vector<uint32_t> readShader(AAssetManager* assets, const char* name) {
    std::vector<uint32_t> words;
    if (!assets) return words;
    AAsset* file = AAssetManager_open(assets, name, AASSET_MODE_BUFFER);
    if (!file) return words;
    const auto length = AAsset_getLength(file);
    if (length <= 0 || length % 4 != 0) {
        AAsset_close(file);
        return words;
    }
    words.resize(static_cast<size_t>(length / 4));
    const auto count = AAsset_read(file, words.data(), length);
    AAsset_close(file);
    if (count != length || words[0] != 0x07230203) words.clear();
    return words;
}

bool supportsVulkan13(VkPhysicalDevice physical) {
    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(physical, &props);
    if (props.apiVersion < VK_API_VERSION_1_3) return false;
    VkPhysicalDeviceVulkan13Features v13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
    VkPhysicalDeviceFeatures2 feats{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    feats.pNext = &v13;
    vkGetPhysicalDeviceFeatures2(physical, &feats);
    return v13.dynamicRendering == VK_TRUE;
}
bool hasSwapchain(VkPhysicalDevice physical) {
    uint32_t count = 0;
    if (!ok(vkEnumerateDeviceExtensionProperties(physical, nullptr, &count, nullptr))) return false;
    std::vector<VkExtensionProperties> list(count);
    if (!ok(vkEnumerateDeviceExtensionProperties(physical, nullptr, &count, list.data()))) return false;
    for (const auto& item : list) {
        if (strcmp(item.extensionName, VK_KHR_SWAPCHAIN_EXTENSION_NAME) == 0) return true;
    }
    return false;
}

class NativeRenderer {
public:
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice gpu = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t family = 0;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat format = VK_FORMAT_UNDEFINED;
    VkExtent2D extent{};
    std::vector<VkImage> images;
    std::vector<VkImageLayout> layouts;
    VkShaderModule vert = VK_NULL_HANDLE;
    VkShaderModule frag = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkCommandPool cmdPool = VK_NULL_HANDLE;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    VkSemaphore acquired = VK_NULL_HANDLE;
    VkSemaphore completed = VK_NULL_HANDLE;
    PFN_vkCmdBeginRendering beginRendering = nullptr;
    PFN_vkCmdEndRendering endRendering = nullptr;
    bool started = false;

    ~NativeRenderer() { destroy(); }

    void destroy() {
        if (device != VK_NULL_HANDLE) vkDeviceWaitIdle(device);
        if (device != VK_NULL_HANDLE) {
            if (cmdPool) vkDestroyCommandPool(device, cmdPool, nullptr);
            if (acquired) vkDestroySemaphore(device, acquired, nullptr);
            if (completed) vkDestroySemaphore(device, completed, nullptr);
            if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
            if (pipelineLayout) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
            if (vert) vkDestroyShaderModule(device, vert, nullptr);
            if (frag) vkDestroyShaderModule(device, frag, nullptr);
            if (swapchain) vkDestroySwapchainKHR(device, swapchain, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (instance != VK_NULL_HANDLE) {
            if (surface) vkDestroySurfaceKHR(instance, surface, nullptr);
            vkDestroyInstance(instance, nullptr);
        }
        if (window) ANativeWindow_release(window);
        window = nullptr;
        instance = VK_NULL_HANDLE;
        device = VK_NULL_HANDLE;
        surface = VK_NULL_HANDLE;
        started = false;
    }

    bool create(ANativeWindow* nativeWindow, AAssetManager* assets) {
        window = nativeWindow; // Already ref-counted by ANativeWindow_fromSurface.
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName = "HALYN Motion";
        app.apiVersion = VK_API_VERSION_1_3;
        const char* instanceExt[] = {VK_KHR_SURFACE_EXTENSION_NAME,
                                     VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        ici.pApplicationInfo = &app;
        ici.enabledExtensionCount = 2;
        ici.ppEnabledExtensionNames = instanceExt;
        if (!ok(vkCreateInstance(&ici, nullptr, &instance))) {
            warn("Instance creation failed"); return false;
        }
        VkAndroidSurfaceCreateInfoKHR asci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
        asci.window = window;
        if (!ok(vkCreateAndroidSurfaceKHR(instance, &asci, nullptr, &surface))) {
            warn("Android surface creation failed"); return false;
        }
        uint32_t count = 0;
        if (!ok(vkEnumeratePhysicalDevices(instance, &count, nullptr)) || count == 0) return false;
        std::vector<VkPhysicalDevice> devices(count);
        if (!ok(vkEnumeratePhysicalDevices(instance, &count, devices.data()))) return false;
        bool found = false;
        for (VkPhysicalDevice candidate : devices) {
            if (!supportsVulkan13(candidate) || !hasSwapchain(candidate)) continue;
            uint32_t families = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &families, nullptr);
            std::vector<VkQueueFamilyProperties> props(families);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &families, props.data());
            for (uint32_t i = 0; i < families; ++i) {
                VkBool32 present = VK_FALSE;
                vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &present);
                if ((props[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                    family = i; gpu = candidate; found = true; break;
                }
            }
            if (found) break;
        }
        if (!found) { warn("No compatible Vulkan 1.3 graphics/present queue"); return false; }

        float priority = 1.f;
        VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        qi.queueFamilyIndex = family;
        qi.queueCount = 1;
        qi.pQueuePriorities = &priority;
        const char* deviceExt[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        VkPhysicalDeviceVulkan13Features v13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
        v13.dynamicRendering = VK_TRUE;
        VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        dci.pNext = &v13;
        dci.queueCreateInfoCount = 1;
        dci.pQueueCreateInfos = &qi;
        dci.enabledExtensionCount = 1;
        dci.ppEnabledExtensionNames = deviceExt;
        if (!ok(vkCreateDevice(gpu, &dci, nullptr, &device))) {
            warn("Vulkan device creation failed"); return false;
        }
        vkGetDeviceQueue(device, family, 0, &queue);
        beginRendering = reinterpret_cast<PFN_vkCmdBeginRendering>(
            vkGetDeviceProcAddr(device, "vkCmdBeginRendering"));
        endRendering = reinterpret_cast<PFN_vkCmdEndRendering>(
            vkGetDeviceProcAddr(device, "vkCmdEndRendering"));
        if (!beginRendering || !endRendering) return false;

        VkSurfaceCapabilitiesKHR caps{};
        if (!ok(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, surface, &caps))) return false;
        uint32_t formats = 0;
        if (!ok(vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, &formats, nullptr)) ||
            formats == 0) return false;
        std::vector<VkSurfaceFormatKHR> available(formats);
        vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, &formats, available.data());
        auto selected = available.front();
        for (const auto& candidate : available) {
            if (candidate.format == VK_FORMAT_R8G8B8A8_UNORM ||
                candidate.format == VK_FORMAT_B8G8R8A8_UNORM) {
                selected = candidate;
                break;
            }
        }
        format = selected.format;
        extent = caps.currentExtent;
        if (extent.width == UINT32_MAX) {
            extent.width = static_cast<uint32_t>(ANativeWindow_getWidth(window));
            extent.height = static_cast<uint32_t>(ANativeWindow_getHeight(window));
            extent.width = std::clamp(extent.width, caps.minImageExtent.width, caps.maxImageExtent.width);
            extent.height = std::clamp(extent.height, caps.minImageExtent.height, caps.maxImageExtent.height);
        }
        if (extent.width == 0 || extent.height == 0) return false;
        uint32_t countImages = std::max(2u, caps.minImageCount);
        if (caps.maxImageCount) countImages = std::min(countImages, caps.maxImageCount);
        VkSwapchainCreateInfoKHR sci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        sci.surface = surface;
        sci.minImageCount = countImages;
        sci.imageFormat = format;
        sci.imageColorSpace = selected.colorSpace;
        sci.imageExtent = extent;
        sci.imageArrayLayers = 1;
        sci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        sci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        sci.preTransform = caps.currentTransform;
        sci.compositeAlpha = (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) ?
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR : VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
        sci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        sci.clipped = VK_TRUE;
        if (!ok(vkCreateSwapchainKHR(device, &sci, nullptr, &swapchain))) {
            warn("Swapchain creation failed"); return false;
        }
        uint32_t actualImages = 0;
        vkGetSwapchainImagesKHR(device, swapchain, &actualImages, nullptr);
        images.resize(actualImages);
        vkGetSwapchainImagesKHR(device, swapchain, &actualImages, images.data());
        layouts.assign(actualImages, VK_IMAGE_LAYOUT_UNDEFINED);

        const auto vertexWords = readShader(assets, "shaders/raytrace.vert.spv");
        const auto fragmentWords = readShader(assets, "shaders/raytrace.frag.spv");
        if (vertexWords.empty() || fragmentWords.empty()) {
            warn("Missing compiled SPIR-V shaders"); return false;
        }
        VkShaderModuleCreateInfo sh{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        sh.codeSize = vertexWords.size() * sizeof(uint32_t);
        sh.pCode = vertexWords.data();
        if (!ok(vkCreateShaderModule(device, &sh, nullptr, &vert))) return false;
        sh.codeSize = fragmentWords.size() * sizeof(uint32_t);
        sh.pCode = fragmentWords.data();
        if (!ok(vkCreateShaderModule(device, &sh, nullptr, &frag))) return false;
        VkPushConstantRange pc{};
        pc.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
        pc.size = sizeof(Push);
        VkPipelineLayoutCreateInfo pli{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        pli.pushConstantRangeCount = 1;
        pli.pPushConstantRanges = &pc;
        if (!ok(vkCreatePipelineLayout(device, &pli, nullptr, &pipelineLayout))) return false;

        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vert;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = frag;
        stages[1].pName = "main";
        VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        vp.viewportCount = 1;
        vp.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        rs.polygonMode = VK_POLYGON_MODE_FILL;
        rs.cullMode = VK_CULL_MODE_NONE;
        rs.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        rs.lineWidth = 1.f;
        VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState attachment{};
        attachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                    VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo blend{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        blend.attachmentCount = 1;
        blend.pAttachments = &attachment;
        const VkDynamicState dyns[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dyn{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
        dyn.dynamicStateCount = 2;
        dyn.pDynamicStates = dyns;
        VkPipelineRenderingCreateInfo ri{VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO};
        ri.colorAttachmentCount = 1;
        ri.pColorAttachmentFormats = &format;
        VkGraphicsPipelineCreateInfo gp{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        gp.pNext = &ri;
        gp.stageCount = 2;
        gp.pStages = stages;
        gp.pVertexInputState = &vi;
        gp.pInputAssemblyState = &ia;
        gp.pViewportState = &vp;
        gp.pRasterizationState = &rs;
        gp.pMultisampleState = &ms;
        gp.pColorBlendState = &blend;
        gp.pDynamicState = &dyn;
        gp.layout = pipelineLayout;
        if (!ok(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &gp, nullptr, &pipeline))) {
            warn("Graphics pipeline creation failed"); return false;
        }

        VkCommandPoolCreateInfo cp{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        cp.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        cp.queueFamilyIndex = family;
        if (!ok(vkCreateCommandPool(device, &cp, nullptr, &cmdPool))) return false;
        VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        alloc.commandPool = cmdPool;
        alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        alloc.commandBufferCount = 1;
        if (!ok(vkAllocateCommandBuffers(device, &alloc, &cmd))) return false;
        VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        if (!ok(vkCreateSemaphore(device, &sem, nullptr, &acquired))) return false;
        if (!ok(vkCreateSemaphore(device, &sem, nullptr, &completed))) return false;
        started = true;
        return true;
    }

    bool draw(const Push& params) {
        if (!started) return false;
        uint32_t index = 0;
        VkResult result = vkAcquireNextImageKHR(device, swapchain, UINT64_MAX,
                                                acquired, VK_NULL_HANDLE, &index);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) return false;
        if (index >= images.size() || !ok(vkResetCommandBuffer(cmd, 0))) return false;
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        if (!ok(vkBeginCommandBuffer(cmd, &begin))) return false;

        VkImageMemoryBarrier before{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        before.oldLayout = layouts[index];
        before.newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        before.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        before.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        before.image = images[index];
        before.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        before.subresourceRange.levelCount = 1;
        before.subresourceRange.layerCount = 1;
        before.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                             VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                             0, 0, nullptr, 0, nullptr, 1, &before);

        VkImageView view = VK_NULL_HANDLE;
        VkImageViewCreateInfo vi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        vi.image = images[index];
        vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
        vi.format = format;
        vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        vi.subresourceRange.levelCount = 1;
        vi.subresourceRange.layerCount = 1;
        // Images are acquired and consumed one at a time. Views are destroyed
        // after waiting for the submitted queue at the end of this frame.
        if (!ok(vkCreateImageView(device, &vi, nullptr, &view))) return false;

        VkRenderingAttachmentInfo color{VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO};
        color.imageView = view;
        color.imageLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        color.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        VkRenderingInfo info{VK_STRUCTURE_TYPE_RENDERING_INFO};
        info.renderArea.extent = extent;
        info.layerCount = 1;
        info.colorAttachmentCount = 1;
        info.pColorAttachments = &color;
        beginRendering(cmd, &info);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        VkViewport viewport{0.f, 0.f,
                            static_cast<float>(extent.width),
                            static_cast<float>(extent.height), 0.f, 1.f};
        VkRect2D scissor{};
        scissor.extent = extent;
        vkCmdSetViewport(cmd, 0, 1, &viewport);
        vkCmdSetScissor(cmd, 0, 1, &scissor);
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0,
                           sizeof(Push), &params);
        vkCmdDraw(cmd, 3, 1, 0, 0);
        endRendering(cmd);

        VkImageMemoryBarrier after{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        after.oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        after.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        after.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        after.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        after.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        after.image = images[index];
        after.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        after.subresourceRange.levelCount = 1;
        after.subresourceRange.layerCount = 1;
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                             VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                             0, 0, nullptr, 0, nullptr, 1, &after);
        if (!ok(vkEndCommandBuffer(cmd))) {
            vkDestroyImageView(device, view, nullptr); return false;
        }
        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &acquired;
        submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &cmd;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &completed;
        result = vkQueueSubmit(queue, 1, &submit, VK_NULL_HANDLE);
        if (!ok(result)) {
            vkDestroyImageView(device, view, nullptr); return false;
        }
        layouts[index] = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        present.waitSemaphoreCount = 1;
        present.pWaitSemaphores = &completed;
        present.swapchainCount = 1;
        present.pSwapchains = &swapchain;
        present.pImageIndices = &index;
        const VkResult presented = vkQueuePresentKHR(queue, &present);
        // One in-flight frame: simpler teardown on wallpaper hiding/surface
        // replacement. No native thread continues after Engine destruction.
        const VkResult idle = vkQueueWaitIdle(queue);
        vkDestroyImageView(device, view, nullptr);
        return (presented == VK_SUCCESS || presented == VK_SUBOPTIMAL_KHR) && idle == VK_SUCCESS;
    }
};

bool probeSupport() {
    uint32_t version = 0;
    if (!ok(vkEnumerateInstanceVersion(&version)) || version < VK_API_VERSION_1_3) return false;
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.apiVersion = VK_API_VERSION_1_3;
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    if (!ok(vkCreateInstance(&ci, nullptr, &instance))) return false;
    uint32_t count = 0;
    bool supported = false;
    if (ok(vkEnumeratePhysicalDevices(instance, &count, nullptr)) && count) {
        std::vector<VkPhysicalDevice> devices(count);
        if (ok(vkEnumeratePhysicalDevices(instance, &count, devices.data()))) {
            for (auto gpu : devices) {
                if (supportsVulkan13(gpu) && hasSwapchain(gpu)) {
                    supported = true; break;
                }
            }
        }
    }
    vkDestroyInstance(instance, nullptr);
    return supported;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oddf0x_halynmotion_VulkanBridge_nativeSupported(JNIEnv*, jobject) {
    return probeSupport() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oddf0x_halynmotion_VulkanBridge_nativeCreate(
    JNIEnv* env, jobject, jobject surface, jobject assetManager) {
    if (!surface || !assetManager) return 0;
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return 0;
    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    auto render = std::make_unique<NativeRenderer>();
    if (!render->create(window, assets)) return 0;
    return reinterpret_cast<jlong>(render.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oddf0x_halynmotion_VulkanBridge_nativeDraw(
    JNIEnv*, jobject, jlong handle, jfloat time, jint variant, jint quality,
    jfloat red, jfloat green, jfloat blue, jfloat intensity,
    jfloat offsetX, jfloat offsetY, jfloat depth, jfloat gloss) {
    auto* renderer = reinterpret_cast<NativeRenderer*>(handle);
    if (!renderer) return JNI_FALSE;
    Push params{};
    params.frame[0] = time;
    params.frame[1] = static_cast<float>(variant);
    params.frame[2] = static_cast<float>(quality);
    params.frame[3] = intensity;
    params.color[0] = red;
    params.color[1] = green;
    params.color[2] = blue;
    params.color[3] = depth;
    params.motion[0] = offsetX;
    params.motion[1] = offsetY;
    params.motion[2] = gloss;
    params.res[0] = static_cast<float>(renderer->extent.width);
    params.res[1] = static_cast<float>(renderer->extent.height);
    return renderer->draw(params) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_oddf0x_halynmotion_VulkanBridge_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<NativeRenderer*>(handle);
}
