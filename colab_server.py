
# =================================================================================
# COPY THIS ENTIRE SCRIPT INTO A CELL IN YOUR GOOGLE COLAB NOTEBOOK (Untitled9.ipynb)
# AND RUN IT AFTER TRAINING IS COMPLETE.
# =================================================================================

import os
import sys

# 1. Install Dependencies
print("Installing dependencies...")
!pip install flask-ngrok pyngrok flask torch torchvision timm opencv-python-headless Pillow

import torch
import torch.nn as nn
from torchvision import transforms
from PIL import Image
import timm
import cv2
import numpy as np
from flask import Flask, request, jsonify
from pyngrok import ngrok
import threading

# ===========================================================
# 2. MODEL DEFINITIONS (Must match training code)
# ===========================================================

class ChannelAttention(nn.Module):
    def __init__(self, in_channels, reduction=16):
        super().__init__()
        self.avg_pool = nn.AdaptiveAvgPool2d(1)
        self.max_pool = nn.AdaptiveMaxPool2d(1)
        self.fc = nn.Sequential(
            nn.Conv2d(in_channels, in_channels // reduction, 1, bias=False),
            nn.ReLU(),
            nn.Conv2d(in_channels // reduction, in_channels, 1, bias=False)
        )
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        avg_out = self.fc(self.avg_pool(x))
        max_out = self.fc(self.max_pool(x))
        out = avg_out + max_out
        return x * self.sigmoid(out)

class SpatialAttention(nn.Module):
    def __init__(self, kernel_size=7):
        super().__init__()
        self.conv = nn.Conv2d(2, 1, kernel_size, padding=kernel_size//2, bias=False)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        avg_out = torch.mean(x, dim=1, keepdim=True)
        max_out, _ = torch.max(x, dim=1, keepdim=True)
        concat = torch.cat([avg_out, max_out], dim=1)
        attention = self.sigmoid(self.conv(concat))
        return x * attention

class CBAM(nn.Module):
    def __init__(self, in_channels, reduction=16, kernel_size=7):
        super().__init__()
        self.channel_attention = ChannelAttention(in_channels, reduction)
        self.spatial_attention = SpatialAttention(kernel_size)

    def forward(self, x):
        x = self.channel_attention(x)
        x = self.spatial_attention(x)
        return x

class EfficientNetWithAttention(nn.Module):
    def __init__(self, num_classes=5, model_name='efficientnet_b2', pretrained=True):
        super().__init__()
        self.backbone = timm.create_model(model_name, pretrained=pretrained, features_only=True)
        self.feature_dims = self.backbone.feature_info.channels()
        self.attention1 = CBAM(self.feature_dims[2])
        self.attention2 = CBAM(self.feature_dims[3])
        self.attention3 = CBAM(self.feature_dims[4])
        self.global_pool = nn.AdaptiveAvgPool2d(1)
        classifier_input = self.feature_dims[-1]
        self.classifier = nn.Sequential(
            nn.Dropout(0.3),
            nn.Linear(classifier_input, 1024),
            nn.BatchNorm1d(1024),
            nn.SiLU(),
            nn.Dropout(0.2),
            nn.Linear(1024, 512),
            nn.BatchNorm1d(512),
            nn.SiLU(),
            nn.Dropout(0.1),
            nn.Linear(512, 256),
            nn.BatchNorm1d(256),
            nn.SiLU(),
            nn.Linear(256, num_classes)
        )

    def forward(self, x):
        features = self.backbone(x)
        f2 = self.attention1(features[2])
        f3 = self.attention2(features[3])
        f4 = self.attention3(features[4])
        x = self.global_pool(f4)
        x = x.flatten(1)
        x = self.classifier(x)
        return x

def create_model(model_type='efficientnet', num_classes=5):
    return EfficientNetWithAttention(num_classes=num_classes)

# ===========================================================
# 3. ANALYSIS PIPELINE
# ===========================================================

class FaceAnalysisServer:
    def __init__(self, model_path='best_model_member04.pth'):
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.classes = ['Heart', 'Oblong', 'Oval', 'Round', 'Square']
        
        print(f"Loading model on {self.device}...")
        self.model = create_model('efficientnet', num_classes=len(self.classes))
        
        if os.path.exists(model_path):
            checkpoint = torch.load(model_path, map_location=self.device)
            self.model.load_state_dict(checkpoint['model_state_dict'])
            print("✅ Model loaded successfully.")
        else:
            print(f"⚠️ Warning: {model_path} not found. Using untrained weights.")
            
        self.model.to(self.device)
        self.model.eval()
        
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        self.transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

    def predict(self, image_bytes):
        # Decode image
        nparr = np.frombuffer(image_bytes, np.uint8)
        img_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img_bgr is None:
            return None, "Invalid image data"
            
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
        
        # Face Detection
        faces = self.face_cascade.detectMultiScale(gray, 1.1, 4)
        
        if len(faces) == 0:
            face_img = img_rgb # Use full image
        else:
            x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
            padding = int(w * 0.1)
            h_img, w_img, _ = img_rgb.shape
            x1 = max(0, x - padding)
            y1 = max(0, y - padding)
            x2 = min(w_img, x + w + padding)
            y2 = min(h_img, y + h + padding)
            face_img = img_rgb[y1:y2, x1:x2]

        # Inference
        pil_img = Image.fromarray(face_img)
        input_tensor = self.transform(pil_img).unsqueeze(0).to(self.device)

        with torch.no_grad():
            outputs = self.model(input_tensor)
            probs = torch.nn.functional.softmax(outputs, dim=1)
            conf, pred = torch.max(probs, 1)
            
        return {
            "face_shape": self.classes[pred.item()],
            "confidence": conf.item() * 100
        }, None

# ===========================================================
# 4. FLASK APP & NGROK
# ===========================================================

app = Flask(__name__)
pipeline = FaceAnalysisServer()

# SET YOUR NGROK AUTHTOKEN HERE!
# ngrok.set_auth_token("YOUR_AUTHTOKEN_HERE") 

@app.route('/predict', methods=['POST'])
def predict():
    if 'image' not in request.files:
        return jsonify({"error": "No image file provided"}), 400
    
    file = request.files['image']
    image_bytes = file.read()
    
    result, error = pipeline.predict(image_bytes)
    
    if error:
        return jsonify({"error": error}), 500
        
    return jsonify(result)

@app.route('/', methods=['GET'])
def home():
    return "Face Analysis API is Running!"

# Open a ngrok tunnel to the HTTP server
public_url = ngrok.connect(5000).public_url
print(f" * Ngrok Tunnel URL: {public_url}")

# Update base URL for client
app.config["BASE_URL"] = public_url

app.run(port=5000)
