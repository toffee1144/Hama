import uuid
import base64
from flask import Flask, request, jsonify, abort
from flask_cors import CORS
from openai import OpenAI

# Load API key (from env.py or environment)
try:
    from env import API_KEY
except ImportError:
    import os
    API_KEY = os.getenv("OPENAI_API_KEY")
    if not API_KEY:
        raise RuntimeError("Missing OpenAI API key")

app = Flask(__name__)
CORS(app)  # ← allow cross‐origin (e.g. from your Android emulator or device)

client = OpenAI(api_key=API_KEY)

# Simple in‐memory store for text prompts if you still want to keep track
PROMPTS = {}

def read_image_as_b64(file_storage):
    """Helper: read werkzeug.FileStorage and return data:image/jpeg;base64,…"""
    img_bytes = file_storage.read()
    prefix = file_storage.mimetype or "image/jpeg"
    b64 = base64.b64encode(img_bytes).decode("utf-8")
    return f"data:{prefix};base64,{b64}"

@app.route("/api/message", methods=["POST"])
def handle_message():
    """
    Single entrypoint for:
      • Text-only prompts (JSON)
      • Image-only (multipart)
      • Text + image (multipart)
    """
    is_json = request.is_json
    prompt = None
    image_b64_url = None

    # 1) JSON body → expect {"prompt": "..."}
    if is_json:
        data = request.get_json()
        prompt = data.get("prompt")
        if not prompt:
            return jsonify({"error": "JSON body must include non-empty 'prompt'"}), 400

    # 2) multipart/form-data → may include 'prompt' and/or 'image'
    else:
        prompt = request.form.get("prompt")
        img_file = request.files.get("image")
        if img_file:
            image_b64_url = read_image_as_b64(img_file)

        # if neither prompt nor image provided, that's an error
        if not prompt and not image_b64_url:
            return jsonify({"error": "multipart/form-data must include 'prompt' and/or 'image'"}), 400

    # Build the chat message sequence
    messages = []
    # system instruction depends on content
    if image_b64_url and prompt:
        messages.append({
            "role": "system",
            "content": "You are an AI assistant that integrates text and images."
        })
    elif image_b64_url:
        messages.append({
            "role": "system",
            "content": "You are an AI assistant that describes images."
        })
    else:
        messages.append({
            "role": "system",
            "content": "You are an AI assistant that answers text prompts."
        })

    # user content block
    if prompt:
        messages.append({"role": "user", "content": prompt})

    if image_b64_url:
        messages.append({
            "role": "user",
            "content": [
                {"type": "text", "text": prompt or "Please describe this image."},
                {"type": "image_url", "image_url": {"url": image_b64_url}}
            ]
        })

    # Call the OpenAI API
    try:
        completion = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages
        )
        ai_response = completion.choices[0].message.content
    except Exception as e:
        return jsonify({"error": f"OpenAI error: {e}"}), 500

    # If you want to store text-only prompts in memory
    if prompt and not image_b64_url:
        prompt_id = str(uuid.uuid4())
        PROMPTS[prompt_id] = {"prompt": prompt, "answer": ai_response}

    return jsonify({"response": ai_response}), 200

@app.route("/api/prompts/<prompt_id>", methods=["GET"])
def get_prompt(prompt_id):
    """Retrieve a previously‐stored text-only prompt/answer by ID."""
    record = PROMPTS.get(prompt_id)
    if not record:
        abort(404)
    return jsonify({
        "id": prompt_id,
        "prompt": record["prompt"],
        "answer": record["answer"]
    }), 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
