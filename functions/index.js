// index.js - Firebase Functions v2 compatible
// -------------------------------------------------
const { onValueCreated, onValueWritten } = require("firebase-functions/v2/database");
const { setGlobalOptions } = require("firebase-functions/v2/options");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const ort = require("onnxruntime-node");
const fetch = require("node-fetch");
const fs = require("fs");
const path = require("path");
const Jimp = require("jimp"); // for image preprocessing

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  databaseURL: "https://foresteye-699bb-default-rtdb.asia-southeast1.firebasedatabase.app",
  storageBucket: "foresteye-699bb.firebasestorage.app"
});
const db = admin.database();
const messaging = admin.messaging();

setGlobalOptions({ region: "asia-southeast1", memory: "1GiB", timeoutSeconds: 300 });

// 🧠 1️⃣ Detect animals when image is uploaded
exports.onImageStored = onValueCreated(  {
    region: "asia-southeast1",  // ✅ match your Firebase project region
    ref: "/images/{imageId}",
  }, async (event) => {
  const data = event.data.val();
  const imageId = event.params.imageId;
  const imageUrl = data.imageUrl;
  const status = data.status;

  if (!imageUrl || status !== "pending") return null;

  logger.info(`🦉 Processing new image: ${imageUrl}`);

  try {
    const os = require("os");
    const tempPath = path.join(os.tmpdir(), `${imageId}.jpg`);

    // 🖼️ Download image from Firebase Storage
    const { getStorage } = require("firebase-admin/storage");
    const bucket = getStorage().bucket();
    const filePath = decodeURIComponent(imageUrl.split("/o/")[1].split("?")[0]);
    const file = bucket.file(filePath);
    await file.download({ destination: tempPath });
    logger.info(`✅ Image downloaded to: ${tempPath}`);

    // 🧩 Load ONNX model
    const modelPath = path.join(__dirname, "model", "best.onnx");
    const session = await ort.InferenceSession.create(modelPath);
    logger.info("✅ ONNX model loaded successfully");

    // 🧮 Preprocess image (resize + normalize)
    const image = await Jimp.read(tempPath);
    image.resize(640, 640);
    const inputSize = 640;

    const imageData = new Float32Array(1 * 3 * inputSize * inputSize);
    let idx = 0;
    for (let y = 0; y < inputSize; y++) {
      for (let x = 0; x < inputSize; x++) {
        const pixel = Jimp.intToRGBA(image.getPixelColor(x, y));
        imageData[idx++] = pixel.r / 255.0; // normalize
        imageData[idx++] = pixel.g / 255.0;
        imageData[idx++] = pixel.b / 255.0;
      }
    }

    const inputTensor = new ort.Tensor("float32", imageData, [1, 3, inputSize, inputSize]);

    // 🔮 Run inference
    const results = await session.run({ images: inputTensor });
    const outputName = session.outputNames[0];
    const outputTensor = results[outputName];

    logger.info("📐 Output tensor info:", {
      type: outputTensor.type,
      dims: outputTensor.dims,
      length: outputTensor.data.length,
    });

    const output = outputTensor.data;

    // ✅ Correct reshape for [1, 24, 8400] → [8400, 24]
    const reshaped = [];
    for (let i = 0; i < 8400; i++) {
      const row = [];
      for (let j = 0; j < 24; j++) {
        row.push(output[j * 8400 + i]);
      }
      reshaped.push(row);
    }

    // ⚙️ Postprocessing
    const labels = [
      "Buffalo", "Camel", "Cat", "Cheetah", "Cow", "Deer", "Dog", "Elephant", "Goat",
      "Gorilla", "Hippo", "Horse", "Lion", "Monkeys", "Panda", "Rat", "Rhino", "Tiger",
      "Wolf", "Zebra",
    ];

    const confidenceThreshold = 0.25;
    const iouThreshold = 0.45;

    const sigmoid = (x) => 1 / (1 + Math.exp(-x));

    let detections = [];

    for (let i = 0; i < reshaped.length; i++) {
      const row = reshaped[i];
      let maxScore = 0;
      let maxClassIndex = -1;

      for (let j = 4; j < 24; j++) {
        const score = sigmoid(row[j]); // apply sigmoid to class scores
        if (score > maxScore) {
          maxScore = score;
          maxClassIndex = j - 4;
        }
      }

      if (maxScore > confidenceThreshold && maxClassIndex >= 0) {
        detections.push({
          class: labels[maxClassIndex],
          confidence: maxScore,
          box: {
            x: row[0],
            y: row[1],
            w: row[2],
            h: row[3],
          },
        });
      }
    }

    // 🧹 Non-Max Suppression
    function iou(boxA, boxB) {
      const xA = Math.max(boxA.x - boxA.w / 2, boxB.x - boxB.w / 2);
      const yA = Math.max(boxA.y - boxA.h / 2, boxB.y - boxB.h / 2);
      const xB = Math.min(boxA.x + boxA.w / 2, boxB.x + boxB.w / 2);
      const yB = Math.min(boxA.y + boxA.h / 2, boxB.y + boxB.h / 2);
      const interArea = Math.max(0, xB - xA) * Math.max(0, yB - yA);
      const boxAArea = boxA.w * boxA.h;
      const boxBArea = boxB.w * boxB.h;
      return interArea / (boxAArea + boxBArea - interArea);
    }

    detections.sort((a, b) => b.confidence - a.confidence);
    const finalDetections = [];
    while (detections.length > 0) {
      const best = detections.shift();
      finalDetections.push(best);
      detections = detections.filter((det) => iou(best.box, det.box) < iouThreshold);
    }

    if (finalDetections.length === 0) {
      logger.info("✅ No confident detections found.");
      await event.data.ref.update({ status: "clear" });
      fs.unlinkSync(tempPath);
      return null;
    }

    const bestDetection = finalDetections[0];
    logger.info(`🚨 Best detection: ${bestDetection.class} (${bestDetection.confidence.toFixed(3)})`);

    // 🧾 Update Firebase
    await event.data.ref.update({
      status: "animal_detected",
      animal: bestDetection.class,
      confidence: bestDetection.confidence,
      detectedAt: Date.now(),
    });

    await db.ref("alerts/current_status").set({
      detected: true,
      animal: bestDetection.class,
      confidence: bestDetection.confidence,
      imageUrl: imageUrl,
      requestLocation: true,
      timestamp: Date.now(),
    });

    fs.unlinkSync(tempPath);
    return null;
  } catch (err) {
    logger.error("🔥 onImageStored error:", err);
    await event.data.ref.update({ status: "error", error: err.message });
    return null;
  }
});



// 📍 2️⃣ When ESP32 sends GPS → finalize detection record + send app notification
// 📍 2️⃣ When ESP32 sends GPS → finalize detection record + send app notification
exports.onLocationReceived = onValueWritten( {
    region: "asia-southeast1",  // ✅ same region
    ref: "/alerts/current_status/location",
  }, async (event) => {
  const newLocation = event.data.after.val();
  if (!newLocation) return null;

  const alertSnap = await db.ref("/alerts/current_status").get();
  const alertData = alertSnap.val();
  if (!alertData?.detected) return null;

  const detId = "det" + Date.now();

  try {
    // 🧭 Reverse Geocoding (OpenStreetMap Nominatim - free, no key)
    let locationText = "Unknown Area";
    if (newLocation.lat && newLocation.lon) {
      try {
        const response = await fetch(
          `https://nominatim.openstreetmap.org/reverse?format=json&lat=${newLocation.lat}&lon=${newLocation.lon}`,
          {
            headers: {
              "User-Agent": "ForestEye/1.0 (foresteye-detection-system@gmail.com)"
            },
            timeout: 5000 // fail fast if no response
          }
        );

        if (response.ok) {
          const geoData = await response.json();
          locationText = geoData.display_name || "Unnamed Forest Zone";
        } else {
          logger.warn(`🌍 Reverse geocoding failed: HTTP ${response.status}`);
        }
      } catch (geoErr) {
        logger.warn("⚠️ Reverse geocoding error:", geoErr.message);
      }
    }

    // 🔄 Convert image to Base64 for direct display in app
    const imageBase64 = await fetch(alertData.imageUrl)
      .then((res) => res.buffer())
      .then((buf) => buf.toString("base64"));

    // 🧾 Write detection record
    await db.ref(`/detections/${detId}`).set({
      animal: alertData.animal || "Unknown",
      confidence: alertData.confidence || null,
      imageUrl: alertData.imageUrl,
      location: {
        lat: newLocation.lat,
        lon: newLocation.lon,
        text: locationText
      },
      timestamp: new Date().toLocaleString("en-IN")
    });

    logger.info(`✅ Detection logged: ${detId} (${locationText})`);

    // 📱 Send push notification to app users (topic: "foresteye_alerts")
        // 📱 Send push notification to app users (topic: "foresteye_alerts")
    const message = {
      notification: {
        title: "🚨 Wildlife Movement Detected!",
        body: `Detected near: ${locationText}`,
        image: `data:image/jpeg;base64,${imageBase64}` // ✅ show the actual image instead of URL
      },
      data: {
        // ✅ Include all fields from alertData except 'animal'
        confidence: alertData.confidence?.toString() || "",
        lat: newLocation.lat?.toString() || "",
        lon: newLocation.lon?.toString() || "",
        locationText: locationText || "",
        detected: alertData.detected?.toString() || "true",
        imageBase64: imageBase64, // send the actual Base64 image for in-app display
        timestamp: new Date().toLocaleString("en-IN"),
      },
      topic: "foresteye_alerts",
    };

    await messaging.send(message);
    logger.info("📩 Notification (with all fields except animal) sent successfully.");

  } catch (err) {
    logger.error("🔥 onLocationReceived error:", err);
    return null;
  }
});
