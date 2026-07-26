import argparse
import json
from pathlib import Path

import cv2
import MNN
import ncnn
import numpy as np
import onnxruntime as ort


OUTPUT_NAMES = (
    "cls_8", "cls_16", "cls_32",
    "obj_8", "obj_16", "obj_32",
    "bbox_8", "bbox_16", "bbox_32",
    "kps_8", "kps_16", "kps_32",
)


def mnn_outputs(model_path: Path, input_tensor: np.ndarray) -> list[np.ndarray]:
    interpreter = MNN.Interpreter(str(model_path))
    session = interpreter.createSession({"backend": "CPU", "numThread": 2})
    device_input = interpreter.getSessionInput(session, "input")
    host_input = MNN.Tensor(
        (1, 3, 320, 320),
        MNN.Halide_Type_Float,
        input_tensor,
        MNN.Tensor_DimensionType_Caffe,
    )
    device_input.copyFrom(host_input)
    interpreter.runSession(session)
    result = []
    for name in OUTPUT_NAMES:
        device_output = interpreter.getSessionOutput(session, name)
        shape = tuple(device_output.getShape())
        host_output = MNN.Tensor(
            shape,
            MNN.Halide_Type_Float,
            np.zeros(shape, dtype=np.float32),
            MNN.Tensor_DimensionType_Caffe,
        )
        device_output.copyToHostTensor(host_output)
        result.append(np.asarray(host_output.getData(), dtype=np.float32).reshape(-1))
    return result


def ncnn_outputs(
    param_path: Path,
    bin_path: Path,
    input_tensor: np.ndarray,
) -> list[np.ndarray]:
    network = ncnn.Net()
    if network.load_param(str(param_path)) != 0:
        raise RuntimeError("ncnn failed to load the exact YuNet param")
    if network.load_model(str(bin_path)) != 0:
        raise RuntimeError("ncnn failed to load the exact YuNet bin")
    extractor = network.create_extractor()
    if extractor.input("in0", ncnn.Mat(input_tensor[0]).clone()) != 0:
        raise RuntimeError("ncnn rejected YuNet input=in0")
    result = []
    for index in range(12):
        code, output = extractor.extract(f"out{index}")
        if code != 0:
            raise RuntimeError(f"ncnn failed to extract out{index}")
        result.append(np.asarray(output).reshape(-1))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--onnx", required=True, type=Path)
    parser.add_argument("--ncnn-param", required=True, type=Path)
    parser.add_argument("--ncnn-bin", required=True, type=Path)
    parser.add_argument("--mnn", required=True, type=Path)
    parser.add_argument("--image", required=True, type=Path)
    parser.add_argument("--absolute-tolerance", type=float, default=1e-4)
    args = parser.parse_args()

    image = cv2.imread(str(args.image), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Failed to read fixed image={args.image}")
    input_tensor = np.ascontiguousarray(
        cv2.resize(image, (320, 320)).astype(np.float32).transpose(2, 0, 1)[None],
    )
    reference = ort.InferenceSession(
        str(args.onnx),
        providers=["CPUExecutionProvider"],
    ).run(list(OUTPUT_NAMES), {"input": input_tensor})
    candidates = {
        "ncnn": ncnn_outputs(args.ncnn_param, args.ncnn_bin, input_tensor),
        "mnn": mnn_outputs(args.mnn, input_tensor),
    }
    report = {}
    for runtime, outputs in candidates.items():
        maxima = {}
        for name, expected, actual in zip(OUTPUT_NAMES, reference, outputs):
            expected_flat = np.asarray(expected).reshape(-1)
            if expected_flat.size != actual.size:
                raise AssertionError(
                    f"{runtime} {name} size={actual.size}, expected={expected_flat.size}",
                )
            maximum = float(np.max(np.abs(expected_flat - actual)))
            maxima[name] = maximum
            if maximum > args.absolute_tolerance:
                raise AssertionError(
                    f"{runtime} {name} maxAbs={maximum} exceeds "
                    f"{args.absolute_tolerance}",
                )
        report[runtime] = {
            "passed": True,
            "maximumAbsoluteError": max(maxima.values()),
            "perOutputMaximumAbsoluteError": maxima,
        }
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
