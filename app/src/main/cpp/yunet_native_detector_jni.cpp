#include <jni.h>
#include <array>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#if INCLUDE_MNN
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#endif
#if INCLUDE_NCNN
#include <ncnn/net.h>
#endif

namespace {

constexpr int kOutputCount = 12;
constexpr int kInputWidth = 320;
constexpr int kInputHeight = 320;
constexpr int kInputChannels = 3;

using Outputs = std::array<std::vector<float>, kOutputCount>;

class YuNetRuntimeHandle {
public:
    virtual ~YuNetRuntimeHandle() = default;
    virtual Outputs run(const float* input, int element_count) = 0;
};

#if INCLUDE_NCNN
class YuNetNcnnHandle final : public YuNetRuntimeHandle {
public:
    YuNetNcnnHandle(const std::string& param_path, const std::string& bin_path) {
        net_.opt.num_threads = 1;
        net_.opt.use_vulkan_compute = false;
        if (net_.load_param(param_path.c_str()) != 0 ||
            net_.load_model(bin_path.c_str()) != 0) {
            throw std::runtime_error("YuNet ncnn failed to load exact model files");
        }
    }

    Outputs run(const float* input, int element_count) override {
        require_input_size(element_count);
        ncnn::Mat tensor(kInputWidth, kInputHeight, kInputChannels);
        std::memcpy(tensor.data, input, element_count * sizeof(float));
        auto extractor = net_.create_extractor();
        if (extractor.input("in0", tensor) != 0) {
            throw std::runtime_error("YuNet ncnn rejected input=in0");
        }
        Outputs outputs;
        for (int index = 0; index < kOutputCount; ++index) {
            ncnn::Mat output;
            const auto name = "out" + std::to_string(index);
            if (extractor.extract(name.c_str(), output) != 0 || output.empty()) {
                throw std::runtime_error("YuNet ncnn missing output=" + name);
            }
            const auto* values = static_cast<const float*>(output.data);
            outputs[index] = std::vector<float>(values, values + output.total());
        }
        return outputs;
    }

private:
    static void require_input_size(int element_count) {
        if (element_count != kInputWidth * kInputHeight * kInputChannels) {
            throw std::runtime_error("YuNet ncnn input must be 1x3x320x320");
        }
    }

    ncnn::Net net_;
};
#endif

#if INCLUDE_MNN
class YuNetMnnHandle final : public YuNetRuntimeHandle {
public:
    explicit YuNetMnnHandle(const std::string& model_path)
        : interpreter_(MNN::Interpreter::createFromFile(model_path.c_str())) {
        if (!interpreter_) {
            throw std::runtime_error("YuNet MNN failed to load exact model");
        }
        MNN::ScheduleConfig config;
        config.type = MNN_FORWARD_CPU;
        config.numThread = 2;
        session_ = interpreter_->createSession(config);
        if (!session_) {
            throw std::runtime_error("YuNet MNN failed to create CPU session");
        }
    }

    ~YuNetMnnHandle() override {
        if (interpreter_ && session_) {
            interpreter_->releaseSession(session_);
        }
        delete interpreter_;
    }

    Outputs run(const float* input, int element_count) override {
        if (element_count != kInputWidth * kInputHeight * kInputChannels) {
            throw std::runtime_error("YuNet MNN input must be 1x3x320x320");
        }
        auto* device_input = interpreter_->getSessionInput(session_, "input");
        if (!device_input) {
            throw std::runtime_error("YuNet MNN missing input=input");
        }
        MNN::Tensor host_input(device_input, MNN::Tensor::CAFFE);
        if (host_input.elementSize() != element_count) {
            throw std::runtime_error("YuNet MNN exact input shape mismatch");
        }
        std::memcpy(host_input.host<float>(), input, element_count * sizeof(float));
        if (!device_input->copyFromHostTensor(&host_input)) {
            throw std::runtime_error("YuNet MNN input copy failed");
        }
        if (interpreter_->runSession(session_) != MNN::NO_ERROR) {
            throw std::runtime_error("YuNet MNN inference failed");
        }
        Outputs outputs;
        for (int index = 0; index < kOutputCount; ++index) {
            const auto* name = kMnnOutputNames[index];
            auto* device_output = interpreter_->getSessionOutput(session_, name);
            if (!device_output) {
                throw std::runtime_error(std::string("YuNet MNN missing output=") + name);
            }
            MNN::Tensor host_output(device_output, MNN::Tensor::CAFFE);
            if (!device_output->copyToHostTensor(&host_output)) {
                throw std::runtime_error(std::string("YuNet MNN output copy failed=") + name);
            }
            const auto* values = host_output.host<float>();
            outputs[index] =
                std::vector<float>(values, values + host_output.elementSize());
        }
        return outputs;
    }

private:
    static constexpr std::array<const char*, kOutputCount> kMnnOutputNames = {
        "cls_8", "cls_16", "cls_32",
        "obj_8", "obj_16", "obj_32",
        "bbox_8", "bbox_16", "bbox_32",
        "kps_8", "kps_16", "kps_32",
    };

    MNN::Interpreter* interpreter_ = nullptr;
    MNN::Session* session_ = nullptr;
};
#endif

void throw_java(JNIEnv* env, const std::string& message) {
    const auto exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message.c_str());
}

std::string to_string(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jobjectArray to_java_outputs(JNIEnv* env, const Outputs& outputs) {
    const auto float_array_class = env->FindClass("[F");
    const auto result =
        env->NewObjectArray(kOutputCount, float_array_class, nullptr);
    for (int index = 0; index < kOutputCount; ++index) {
        const auto& output = outputs[index];
        const auto values = env->NewFloatArray(static_cast<jsize>(output.size()));
        env->SetFloatArrayRegion(
            values,
            0,
            static_cast<jsize>(output.size()),
            output.data());
        env->SetObjectArrayElement(result, index, values);
        env->DeleteLocalRef(values);
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_YuNetNativeFaceDetector_00024NativeBridge_createNcnn(
    JNIEnv* env, jobject, jstring param_path, jstring bin_path) {
    try {
#if INCLUDE_NCNN
        return reinterpret_cast<jlong>(
            new YuNetNcnnHandle(to_string(env, param_path), to_string(env, bin_path)));
#else
        throw std::runtime_error("YuNet ncnn was not packaged; no fallback");
#endif
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_YuNetNativeFaceDetector_00024NativeBridge_createMnn(
    JNIEnv* env, jobject, jstring model_path) {
    try {
#if INCLUDE_MNN
        return reinterpret_cast<jlong>(
            new YuNetMnnHandle(to_string(env, model_path)));
#else
        throw std::runtime_error("YuNet MNN was not packaged; no fallback");
#endif
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_YuNetNativeFaceDetector_00024NativeBridge_run(
    JNIEnv* env, jobject, jlong handle_value, jfloatArray input_array) {
    auto* handle = reinterpret_cast<YuNetRuntimeHandle*>(handle_value);
    if (!handle) {
        throw_java(env, "YuNet native runtime handle is closed");
        return nullptr;
    }
    auto* input = env->GetFloatArrayElements(input_array, nullptr);
    try {
        const auto outputs = handle->run(input, env->GetArrayLength(input_array));
        env->ReleaseFloatArrayElements(input_array, input, JNI_ABORT);
        return to_java_outputs(env, outputs);
    } catch (const std::exception& error) {
        env->ReleaseFloatArrayElements(input_array, input, JNI_ABORT);
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_YuNetNativeFaceDetector_00024NativeBridge_close(
    JNIEnv*, jobject, jlong handle_value) {
    delete reinterpret_cast<YuNetRuntimeHandle*>(handle_value);
}
