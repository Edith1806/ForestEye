import onnx

model = onnx.load("./model/best.onnx")
print("✅ Model loaded")

# List all inputs and outputs
for i, input in enumerate(model.graph.input):
    print(f"\n🔹 Input {i}:")
    print(input)

for i, output in enumerate(model.graph.output):
    print(f"\n🔹 Output {i}:")
    print(output)
