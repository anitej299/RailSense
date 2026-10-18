from __future__ import annotations

import io, time, tracemalloc
import numpy as np
import torch


def benchmark_model(model, sample, repetitions=30):
    model.eval(); x = torch.as_tensor(sample[None], dtype=torch.float32)
    with torch.no_grad():
        for _ in range(5): model(x)
    tracemalloc.start(); times=[]
    with torch.no_grad():
        for _ in range(repetitions):
            start=time.perf_counter(); model(x); times.append((time.perf_counter()-start)*1000)
    _, peak=tracemalloc.get_traced_memory(); tracemalloc.stop()
    buffer=io.BytesIO(); torch.save(model.state_dict(), buffer)
    return {"parameters": sum(p.numel() for p in model.parameters()), "model_size_mb": len(buffer.getvalue())/1e6, "cpu_latency_ms": float(np.median(times)), "throughput_windows_s": float(1000/np.mean(times)), "python_peak_memory_mb": peak/1e6}
