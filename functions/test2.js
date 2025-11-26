const ort = require("onnxruntime-node");
const fs = require("fs");
const path = require("path");

(async () => {
  try {
    const modelPath = path.join(__dirname, "model", "best.onnx");
    console.log("🔍 Checking model at:", modelPath);

    if (!fs.existsSync(modelPath)) {
      console.error("❌ Model file not found!");
      return;
    }

    const session = await ort.InferenceSession.create(modelPath);
    console.log("✅ Model loaded successfully");

    console.log("\n🧠 Inputs:");
    for (const name of session.inputNames) {
      const meta = session.inputMetadata[name];
      console.log(`- Name: ${name}`);
      console.log(`  Meta:`, meta || "(no metadata)");
    }

    console.log("\n🎯 Outputs:");
    for (const name of session.outputNames) {
      const meta = session.outputMetadata[name];
      console.log(`- Name: ${name}`);
      console.log(`  Meta:`, meta || "(no metadata)");
    }

  } catch (err) {
    console.error("❌ Error loading model:", err);
  }
})();
