#include <jni.h>
#include <algorithm>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <ncnn/net.h>

namespace {

class RuntimeHandle {
public:
    virtual ~RuntimeHandle() = default;
    virtual std::vector<float> run(const float* input, int width, int height, int channels) = 0;
};

class NcnnHandle final : public RuntimeHandle {
public:
    NcnnHandle(const char* param_path, const char* bin_path) {
        net_.opt.num_threads = 1;
        net_.opt.use_vulkan_compute = false;
        if (net_.load_param(param_path) != 0 || net_.load_model(bin_path) != 0) {
            throw std::runtime_error("ncnn failed to load model files");
        }
        const auto& inputs = net_.input_names();
        const auto& outputs = net_.output_names();
        if (inputs.empty() || outputs.empty()) throw std::runtime_error("ncnn model has no input/output");
        input_name_ = inputs.front();
        output_name_ = outputs.back();
    }

    std::vector<float> run(const float* input, int width, int height, int channels) override {
        ncnn::Mat tensor(width, height, channels);
        std::memcpy(tensor.data, input, tensor.total() * sizeof(float));
        auto extractor = net_.create_extractor();
        if (extractor.input(input_name_.c_str(), tensor) != 0) {
            throw std::runtime_error("ncnn rejected input tensor");
        }
        ncnn::Mat output;
        if (extractor.extract(output_name_.c_str(), output) != 0 || output.empty()) {
            throw std::runtime_error("ncnn inference failed");
        }
        const float* values = static_cast<const float*>(output.data);
        return std::vector<float>(values, values + output.total());
    }

private:
    ncnn::Net net_;
    std::string input_name_;
    std::string output_name_;
};

class MnnHandle final : public RuntimeHandle {
public:
    explicit MnnHandle(const char* model_path)
        : interpreter_(MNN::Interpreter::createFromFile(model_path)) {
        if (!interpreter_) throw std::runtime_error("MNN failed to load model");
        MNN::ScheduleConfig config;
        config.type = MNN_FORWARD_CPU;
        config.numThread = 2;
        session_ = interpreter_->createSession(config);
        if (!session_) throw std::runtime_error("MNN failed to create session");
    }

    ~MnnHandle() override {
        if (interpreter_ && session_) interpreter_->releaseSession(session_);
        delete interpreter_;
    }

    std::vector<float> run(const float* input, int width, int height, int channels) override {
        auto* device_input = interpreter_->getSessionInput(session_, nullptr);
        if (!device_input) throw std::runtime_error("MNN model has no input");
        MNN::Tensor host_input(device_input, MNN::Tensor::CAFFE);
        const int input_size = width * height * channels;
        if (host_input.elementSize() != input_size) throw std::runtime_error("MNN input shape mismatch");
        std::memcpy(host_input.host<float>(), input, input_size * sizeof(float));
        if (!device_input->copyFromHostTensor(&host_input)) throw std::runtime_error("MNN input copy failed");
        if (interpreter_->runSession(session_) != MNN::NO_ERROR) throw std::runtime_error("MNN inference failed");
        auto* device_output = interpreter_->getSessionOutput(session_, nullptr);
        if (!device_output) throw std::runtime_error("MNN model has no output");
        MNN::Tensor host_output(device_output, MNN::Tensor::CAFFE);
        if (!device_output->copyToHostTensor(&host_output)) throw std::runtime_error("MNN output copy failed");
        const float* values = host_output.host<float>();
        return std::vector<float>(values, values + host_output.elementSize());
    }

private:
    MNN::Interpreter* interpreter_ = nullptr;
    MNN::Session* session_ = nullptr;
};

void throw_java(JNIEnv* env, const std::string& message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message.c_str());
}

std::string to_string(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_NativeFaceEmbeddingEngine_00024NativeBridge_createNcnn(
    JNIEnv* env, jobject, jstring param_path, jstring bin_path) {
    try {
        return reinterpret_cast<jlong>(
            new NcnnHandle(to_string(env, param_path).c_str(), to_string(env, bin_path).c_str()));
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_NativeFaceEmbeddingEngine_00024NativeBridge_createMnn(
    JNIEnv* env, jobject, jstring model_path) {
    try {
        return reinterpret_cast<jlong>(new MnnHandle(to_string(env, model_path).c_str()));
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_NativeFaceEmbeddingEngine_00024NativeBridge_run(
    JNIEnv* env, jobject, jlong handle_value, jfloatArray input_array, jint width, jint height, jint channels) {
    auto* handle = reinterpret_cast<RuntimeHandle*>(handle_value);
    if (!handle) {
        throw_java(env, "Native face runtime handle is closed");
        return nullptr;
    }
    jfloat* input = env->GetFloatArrayElements(input_array, nullptr);
    try {
        auto output = handle->run(input, width, height, channels);
        env->ReleaseFloatArrayElements(input_array, input, JNI_ABORT);
        jfloatArray result = env->NewFloatArray(static_cast<jsize>(output.size()));
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(output.size()), output.data());
        return result;
    } catch (const std::exception& error) {
        env->ReleaseFloatArrayElements(input_array, input, JNI_ABORT);
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_pepper_1person_1id_1poc_infrastructure_face_NativeFaceEmbeddingEngine_00024NativeBridge_close(
    JNIEnv*, jobject, jlong handle_value) {
    delete reinterpret_cast<RuntimeHandle*>(handle_value);
}
