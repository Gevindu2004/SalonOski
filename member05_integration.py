
import cv2
import numpy as np
import requests
import torch
import torch.nn as nn
import torchvision.models as models
from torchvision import transforms
from PIL import Image
import timm
import random
import glob
import os

# ===========================================================
# 1. MODEL DEFINITIONS (Extracted from Notebook)
# ===========================================================

class ChannelAttention(nn.Module):
    """Channel Attention Module - CBAM style"""
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
    """Spatial Attention Module - CBAM style"""
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
    """Convolutional Block Attention Module"""
    def __init__(self, in_channels, reduction=16, kernel_size=7):
        super().__init__()
        self.channel_attention = ChannelAttention(in_channels, reduction)
        self.spatial_attention = SpatialAttention(kernel_size)

    def forward(self, x):
        x = self.channel_attention(x)
        x = self.spatial_attention(x)
        return x

class EfficientNetWithAttention(nn.Module):
    """EfficientNet-B2 with CBAM attention"""

    def __init__(self, num_classes=5, model_name='efficientnet_b2', pretrained=True):
        super().__init__()

        # Load base model
        self.backbone = timm.create_model(model_name, pretrained=pretrained, features_only=True)

        # Get feature dimensions
        self.feature_dims = self.backbone.feature_info.channels()

        # Add attention modules at different scales
        self.attention1 = CBAM(self.feature_dims[2])  # Medium features
        self.attention2 = CBAM(self.feature_dims[3])  # Larger features
        self.attention3 = CBAM(self.feature_dims[4])  # Largest features

        # Global pooling
        self.global_pool = nn.AdaptiveAvgPool2d(1)

        # Advanced classifier with multiple layers
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
        # Extract features at different scales
        features = self.backbone(x)

        # Apply attention at different scales
        # features list corresponds to different stages. 
        # Usually indices 2, 3, 4 map to the ones we attached attention to.
        # Note: timm features_only=True returns a list of features.
        f2 = self.attention1(features[2])
        f3 = self.attention2(features[3])
        f4 = self.attention3(features[4])

        # Use the largest features for classification (f4)
        x = self.global_pool(f4)
        x = x.flatten(1)

        # Classify
        x = self.classifier(x)

        return x

class ResNetWithAttention(nn.Module):
    """ResNet50 with CBAM attention"""

    def __init__(self, num_classes=5, pretrained=True):
        super().__init__()

        # Load ResNet50
        self.backbone = models.resnet50(pretrained=pretrained)

        # Add attention after layer3 and layer4
        self.attention3 = CBAM(1024)  # After layer3
        self.attention4 = CBAM(2048)  # After layer4

        # Remove original classifier
        in_features = self.backbone.fc.in_features
        self.backbone.fc = nn.Identity()

        # Advanced classifier
        self.classifier = nn.Sequential(
            nn.Dropout(0.3),
            nn.Linear(in_features, 1024),
            nn.BatchNorm1d(1024),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(1024, 512),
            nn.BatchNorm1d(512),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(512, 256),
            nn.BatchNorm1d(256),
            nn.ReLU(),
            nn.Linear(256, num_classes)
        )

    def forward(self, x):
        # Custom forward with attention
        x = self.backbone.conv1(x)
        x = self.backbone.bn1(x)
        x = self.backbone.relu(x)
        x = self.backbone.maxpool(x)

        x = self.backbone.layer1(x)
        x = self.backbone.layer2(x)
        x = self.backbone.layer3(x)
        x = self.attention3(x)

        x = self.backbone.layer4(x)
        x = self.attention4(x)

        x = self.backbone.avgpool(x)
        x = x.flatten(1)
        x = self.classifier(x)

        return x

class VisionTransformerFace(nn.Module):
    """Vision Transformer for face shapes"""

    def __init__(self, num_classes=5, model_name='vit_base_patch16_224', pretrained=True):
        super().__init__()

        # Load ViT
        self.backbone = timm.create_model(model_name, pretrained=pretrained, num_classes=0)

        # Get feature dimension
        in_features = self.backbone.num_features

        # Classifier head
        self.classifier = nn.Sequential(
            nn.LayerNorm(in_features),
            nn.Dropout(0.2),
            nn.Linear(in_features, 512),
            nn.GELU(),
            nn.Dropout(0.1),
            nn.Linear(512, 256),
            nn.GELU(),
            nn.Linear(256, num_classes)
        )

    def forward(self, x):
        features = self.backbone(x)
        return self.classifier(features)

def create_model(model_type='efficientnet', num_classes=5):
    """Create selected model architecture"""
    if model_type == 'efficientnet':
        model = EfficientNetWithAttention(num_classes=num_classes)
    elif model_type == 'resnet':
        model = ResNetWithAttention(num_classes=num_classes)
    elif model_type == 'vit':
        model = VisionTransformerFace(num_classes=num_classes)
    else:
        print(f"[ERROR] Unknown model type: {model_type}")
        return None
    return model



# ===========================================================
# 3. FACE ANALYSIS PIPELINE
# ===========================================================

class FaceAnalysisPipeline:
    def __init__(self, model_path='best_model_member04.pth', classes=None):
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.classes = classes if classes else ['Heart', 'Oblong', 'Oval', 'Round', 'Square']
        
        # Load Model
        print(f"Loading model from {model_path}...")
        
        # Re-initialize model structure (Must match Member 04)
        self.model = create_model('efficientnet', num_classes=len(self.classes))
        
        if os.path.exists(model_path):
            checkpoint = torch.load(model_path, map_location=self.device)
            self.model.load_state_dict(checkpoint['model_state_dict'])
            print("[OK] Loaded trained model weights.")
        else:
            print(f"[WARNING] Model file {model_path} not found.")
            print("[WARNING] Using UNTRAINED model for demonstration purposes.")
            
        self.model.to(self.device)
        self.model.eval()
        
        # Face Detection
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        
        # Transforms (Must match training)
        self.transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])
        


    def process_image(self, image_path):
        """
        Full pipeline: Load -> Detect Face -> Crop -> Classify
        """
        # 1. Load Image
        try:
            img_bgr = cv2.imread(image_path)
            if img_bgr is None:
                raise ValueError("Could not load image")
            img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        except Exception as e:
            return None, f"Error loading image: {e}"

        # 2. Detect Face
        gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
        faces = self.face_cascade.detectMultiScale(gray, 1.1, 4)
        
        if len(faces) == 0:
            return None, "No human face detected in the image."
        else:
            print(f"Detected {len(faces)} face(s). Using the largest one.")
            # Use largest face
            x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
            # Add some padding
            padding = int(w * 0.1)
            h_img, w_img, _ = img_rgb.shape
            x1 = max(0, x - padding)
            y1 = max(0, y - padding)
            x2 = min(w_img, x + w + padding)
            y2 = min(h_img, y + h + padding)
            face_img = img_rgb[y1:y2, x1:x2]
            
            # Draw rectangle on original
            cv2.rectangle(img_rgb, (x, y), (x+w, y+h), (0, 255, 0), 3)

        # 3. Preprocess for Model
        pil_img = Image.fromarray(face_img)
        input_tensor = self.transform(pil_img).unsqueeze(0).to(self.device)

        # 4. Inference
        with torch.no_grad():
            outputs = self.model(input_tensor)
            probs = torch.nn.functional.softmax(outputs, dim=1)
            conf, pred = torch.max(probs, 1)
            
        face_shape = self.classes[pred.item()]
        confidence = conf.item() * 100
        
        print(f"\\nDetected Face Shape: {face_shape} ({confidence:.2f}%)")

        return {
            "original_image": img_rgb,
            "face_shape": face_shape,
            "confidence": confidence
        }, None

# ===========================================================
# 4. MAIN & TEST
# ===========================================================

if __name__ == "__main__":
    import argparse
    import sys

    # Parse arguments
    parser = argparse.ArgumentParser(description="Face Analysis Pipeline")
    parser.add_argument("--image", type=str, help="Path to input image")
    # 'detect_only' for Java integration (outputs JUST the shape name)
    # 'full' for standalone testing (outputs detailed report + Gemini recs)
    parser.add_argument("--mode", type=str, default="full", choices=["full", "detect_only"], help="Execution mode")
    parser.add_argument("--model", type=str, default="best_model_member04.pth", help="Path to trained model")
    
    args = parser.parse_args()

    try:
        pipeline = FaceAnalysisPipeline(model_path=args.model)
        


        if args.image:
            # Process specific image
            if not os.path.exists(args.image):
                print(f"Error: Image file not found: {args.image}", file=sys.stderr)
                sys.exit(1)
                
            result, error = pipeline.process_image(args.image)
            
            if result:
                if args.mode == "detect_only":
                    # KEY FOR JAVA INTEGRATION: Output ONLY the shape name to stdout
                    print(result['face_shape'])
                else:
                    # Full report
                    print("\n--- RESULTS ---")
                    print(f"Face Shape: {result['face_shape']} ({result['confidence']:.1f}%)")
                    print("-" * 30)
            else:
                print(f"Error: {error}", file=sys.stderr)
                sys.exit(1)

        else:
            # Test mode (default behavior if no image provided)
            # Only valid in 'full' mode
            if args.mode == "detect_only":
                 print("Error: --image is required for detect_only mode", file=sys.stderr)
                 sys.exit(1)

            print("[OK] Pipeline Initialized. Running test on random image...")
            test_path = "/content/augmented_faces/test"  # Default test path
            if os.path.exists(test_path):
                test_images = glob.glob(f"{test_path}/*/*.jpg") + glob.glob(f"{test_path}/*/*.png")
                if test_images:
                    sample_image_path = random.choice(test_images)
                    print(f"Testing on image: {sample_image_path}")
                    result, error = pipeline.process_image(sample_image_path)
                    if result:
                        print(f"\n--- RESULTS ---")
                        print(f"Face Shape: {result['face_shape']} ({result['confidence']:.1f}%)")
                    else:
                        print(f"Processing failed: {error}")
                else:
                    print("No test images found.")
            else:
                print("Test path does not exist.")
            
    except Exception as e:
        print(f"Execution failed: {e}", file=sys.stderr)
        sys.exit(1)
