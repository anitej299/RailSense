from pathlib import Path
import torch


def export_onnx(model, window_shape, output):
    output = Path(output); output.parent.mkdir(parents=True, exist_ok=True)
    model.eval(); dummy = torch.zeros(1, *window_shape)
    torch.onnx.export(model, dummy, output, input_names=["sensor_window"], output_names=["logits"], dynamic_axes={"sensor_window": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17)
    return output

