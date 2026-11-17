# Third-party notices

MealSnap bundles or uses the following. Their licences apply to them, not to MealSnap's own code.

- **AIY food classifier** (`app/src/main/assets/food_classifier.tflite` and `food_labels.csv`):
  Google's "Food classifier" TensorFlow Lite model (MobileNet trained to recognise about 2,000 dishes),
  published by Google on TensorFlow Hub under the Apache License 2.0. The copy in this repository was
  taken from a public GitHub project that redistributes it, and is unmodified apart from format.
- **LiteRT** (Google AI Edge), Apache License 2.0: runs the classifier on the phone.
- **ML Kit GenAI Prompt API** (Google): optional use of on-device Gemini Nano where the phone supports it.
- **Google Gemini API** (optional, with your own key): called over HTTPS; subject to Google's terms.
- **Anthropic Java SDK**, MIT License: used only if you add your own Anthropic API key.
- **AndroidX, Jetpack Compose, Room, Coil, Kotlin and kotlinx libraries**, Apache License 2.0.

The built-in nutrition table (`app/src/main/java/com/mealsnap/nutrition/NutritionData.kt`) was compiled for this
project from typical published food-composition values. Figures are approximate averages and real dishes vary.
