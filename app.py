import os
import json
import shutil
import numpy as np
from fastapi import FastAPI, UploadFile, Form
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
import onnxruntime as ort

# ---------------- 配置 ----------------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "models", "mobilenet_feature_extractor.onnx")
TEMPLATE_DIR = os.path.join(BASE_DIR, "templates_store")
CONFIG_FILE = os.path.join(BASE_DIR, "threshold.json")
DEFAULT_THRESHOLD = 0.7

os.makedirs(TEMPLATE_DIR, exist_ok=True)

# ---------------- 阈值持久化 ----------------
if os.path.exists(CONFIG_FILE):
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        threshold = json.load(f).get("value", DEFAULT_THRESHOLD)
else:
    threshold = DEFAULT_THRESHOLD

# ---------------- 模型 ----------------
session = ort.InferenceSession(MODEL_PATH)
input_name = session.get_inputs()[0].name

def preprocess(img: Image.Image):
    img = img.resize((224, 224))
    arr = np.array(img).astype("float32") / 255.0
    arr = np.transpose(arr, (2, 0, 1))
    return np.expand_dims(arr, axis=0)

def get_embedding(img: Image.Image):
    inputs = {input_name: preprocess(img)}
    emb = session.run(None, inputs)[0][0]
    return emb / np.linalg.norm(emb)

def cosine_similarity(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

def load_embeddings():
    embeddings = {}
    for dish in os.listdir(TEMPLATE_DIR):
        dish_dir = os.path.join(TEMPLATE_DIR, dish)
        emb_file = os.path.join(dish_dir, "embeddings.json")
        if os.path.isdir(dish_dir) and os.path.exists(emb_file):
            with open(emb_file, "r", encoding="utf-8") as f:
                embeddings[dish] = json.load(f)
    return embeddings

# ---------------- FastAPI ----------------
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/static", StaticFiles(directory="static"), name="static")
app.mount("/templates_store", StaticFiles(directory=TEMPLATE_DIR), name="templates_store")

@app.get("/")
async def root():
    return RedirectResponse(url="/static/index.html")

# 添加模板
@app.post("/add_template")
async def add_template(dish: str = Form(...), file: UploadFile = None):
    dish_dir = os.path.join(TEMPLATE_DIR, dish)
    os.makedirs(dish_dir, exist_ok=True)

    img = Image.open(file.file).convert("RGB")
    filename = file.filename
    save_path = os.path.join(dish_dir, filename)
    img.save(save_path)

    emb = get_embedding(img).tolist()
    emb_file = os.path.join(dish_dir, "embeddings.json")
    data = []
    if os.path.exists(emb_file):
        with open(emb_file, "r", encoding="utf-8") as f:
            data = json.load(f)
    data.append({"file": filename, "embedding": emb})
    with open(emb_file, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    return {"status": "ok"}

# 删除某个菜品的一张图片
@app.delete("/delete_template/{dish}/{filename}")
async def delete_template(dish: str, filename: str):
    dish_dir = os.path.join(TEMPLATE_DIR, dish)
    file_path = os.path.join(dish_dir, filename)
    emb_file = os.path.join(dish_dir, "embeddings.json")

    if os.path.exists(file_path):
        os.remove(file_path)

    if os.path.exists(emb_file):
        with open(emb_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        data = [d for d in data if d["file"] != filename]
        with open(emb_file, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    return {"status": "deleted"}

# ⚡ 删除整个菜品（文件夹递归删除）
@app.delete("/delete_dish/{dish}")
async def delete_dish(dish: str):
    dish_dir = os.path.join(TEMPLATE_DIR, dish)
    if os.path.exists(dish_dir):
        shutil.rmtree(dish_dir)
        return {"status": "deleted", "dish": dish}
    return {"error": "菜品不存在"}

# 列出模板
@app.get("/list_templates")
async def list_templates():
    result = {}
    for dish in os.listdir(TEMPLATE_DIR):
        dish_dir = os.path.join(TEMPLATE_DIR, dish)
        emb_file = os.path.join(dish_dir, "embeddings.json")
        if os.path.isdir(dish_dir) and os.path.exists(emb_file):
            with open(emb_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            result[dish] = data
    return result

# 设置阈值（持久化）
@app.post("/set_threshold")
async def set_threshold(value: float = Form(...)):
    global threshold
    threshold = value
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump({"value": threshold}, f)
    return {"status": "ok", "threshold": threshold}

# 获取阈值
@app.get("/get_threshold")
async def get_threshold():
    return {"threshold": threshold}

# 识别接口
@app.post("/predict")
async def predict(file: UploadFile):
    img = Image.open(file.file).convert("RGB")
    emb = get_embedding(img)

    embeddings = load_embeddings()
    best_label, best_score = None, -1

    for dish, items in embeddings.items():
        for item in items:
            score = cosine_similarity(emb, np.array(item["embedding"]))
            if score > best_score:
                best_label, best_score = dish, score

    if best_score >= threshold:
        return {"best": {"label": best_label, "score": best_score}}
    else:
        return {"error": "未识别", "best_score": best_score}
