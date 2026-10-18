from __future__ import annotations

import math
import torch
from torch import nn


class CNN1D(nn.Module):
    def __init__(self, channels=8, classes=4, hidden=32):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv1d(channels, hidden, 7, padding=3), nn.BatchNorm1d(hidden), nn.ReLU(),
            nn.MaxPool1d(2), nn.Conv1d(hidden, hidden * 2, 5, padding=2), nn.ReLU(),
            nn.AdaptiveAvgPool1d(1),
        )
        self.classifier = nn.Linear(hidden * 2, classes)

    def forward(self, x):
        return self.classifier(self.features(x.transpose(1, 2)).squeeze(-1))


class LSTMClassifier(nn.Module):
    def __init__(self, channels=8, classes=4, hidden=32):
        super().__init__()
        self.lstm = nn.LSTM(channels, hidden, batch_first=True, num_layers=1)
        self.classifier = nn.Linear(hidden, classes)

    def forward(self, x):
        output, _ = self.lstm(x)
        return self.classifier(output[:, -1])


class CNNBiLSTM(nn.Module):
    def __init__(self, channels=8, classes=4, hidden=32):
        super().__init__()
        self.conv = nn.Sequential(nn.Conv1d(channels, hidden, 7, padding=3), nn.BatchNorm1d(hidden), nn.ReLU(), nn.MaxPool1d(2))
        self.lstm = nn.LSTM(hidden, hidden, batch_first=True, bidirectional=True)
        self.attention = nn.Linear(hidden * 2, 1)
        self.classifier = nn.Linear(hidden * 2, classes)

    def forward(self, x):
        encoded = self.conv(x.transpose(1, 2)).transpose(1, 2)
        encoded, _ = self.lstm(encoded)
        weights = torch.softmax(self.attention(encoded), dim=1)
        return self.classifier((encoded * weights).sum(dim=1))


class PositionalEncoding(nn.Module):
    def __init__(self, dim, max_len=4096):
        super().__init__()
        position = torch.arange(max_len).unsqueeze(1)
        div = torch.exp(torch.arange(0, dim, 2) * (-math.log(10000.0) / dim))
        pe = torch.zeros(max_len, dim)
        pe[:, 0::2] = torch.sin(position * div)
        pe[:, 1::2] = torch.cos(position * div)
        self.register_buffer("pe", pe.unsqueeze(0))

    def forward(self, x):
        return x + self.pe[:, :x.size(1)]


class LightweightTransformer(nn.Module):
    def __init__(self, channels=8, classes=4, hidden=32, heads=4):
        super().__init__()
        self.projection = nn.Linear(channels, hidden)
        self.position = PositionalEncoding(hidden)
        layer = nn.TransformerEncoderLayer(hidden, heads, hidden * 2, dropout=.1, batch_first=True)
        self.encoder = nn.TransformerEncoder(layer, num_layers=2)
        self.classifier = nn.Linear(hidden, classes)

    def forward(self, x):
        return self.classifier(self.encoder(self.position(self.projection(x))).mean(dim=1))


def build_model(name, channels=8, classes=4, hidden=32):
    choices = {"cnn": CNN1D, "lstm": LSTMClassifier, "cnn_bilstm": CNNBiLSTM, "transformer": LightweightTransformer}
    if name not in choices:
        raise ValueError(f"Unknown deep model: {name}")
    return choices[name](channels, classes, hidden)

