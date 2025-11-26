import onnxruntime as ort
import numpy as np
from PIL import Image

# Load model
sess = ort.InferenceSession("your_model.onnx")

# Load image
img = Image.open("lion.jpg").resize((224,224))
x = np.array(img).astype(np.float32) / 255.0
x = np.transpose(x, (2,0,1))[None, :]  # [1,3,224,224]

# Run inference
outputs = sess.run(None, {sess.get_inputs()[0].name: x})
print("Output shape:", outputs[0].shape)
print("Top 5 indices:", np.argsort(outputs[0][0])[-5:][::-1])
