let video, resultDiv;
let templateImages = [];

// 显示非阻塞提示
function showToast(msg, duration = 2000) {
  const toast = document.getElementById("toast");
  toast.innerText = msg;
  toast.style.display = "block";
  setTimeout(() => {
    toast.style.display = "none";
  }, duration);
}

// 打开摄像头
async function startCamera() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment", width: 960, height: 1280 }
    });
    video.srcObject = stream;
  } catch (err) {
    showToast("无法打开摄像头: " + err);
  }
}

// 拍照临时存入模板
function captureTemplate() {
  const canvas = document.createElement("canvas");
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(video, 0, 0);

  canvas.toBlob(blob => {
    const index = templateImages.length;
    templateImages.push({ blob });

    const wrapper = document.createElement("div");
    wrapper.className = "preview-item";
    wrapper.dataset.index = index;

    const img = document.createElement("img");
    img.src = URL.createObjectURL(blob);

    const btn = document.createElement("button");
    btn.innerText = "×";
    btn.onclick = () => {
      templateImages[index] = null;
      wrapper.remove();
    };

    wrapper.appendChild(img);
    wrapper.appendChild(btn);
    document.getElementById("preview").appendChild(wrapper);
  }, "image/jpeg");
}

// 保存模板
async function saveTemplate() {
  const name = document.getElementById("dishName").value;
  if (!name || templateImages.filter(i => i).length === 0) {
    showToast("请输入菜品名并至少拍一张照片");
    return;
  }

  for (let i = 0; i < templateImages.length; i++) {
    if (!templateImages[i]) continue;
    const formData = new FormData();
    formData.append("dish", name);
    formData.append("file", templateImages[i].blob, `capture_${i}.jpg`);
    await fetch("/add_template", { method: "POST", body: formData });
  }

  showToast("模板已保存！");
  templateImages = [];
  document.getElementById("preview").innerHTML = "";
  document.getElementById("dishName").value = "";

  listTemplates(); // ✅ 只刷新模板，不重启相机
}

// 显示模板
async function listTemplates() {
  const res = await fetch("/list_templates");
  const data = await res.json();

  const container = document.getElementById("templates");
  container.innerHTML = "";

  Object.keys(data).forEach(dish => {
    const section = document.createElement("div");
    const title = document.createElement("h4");
    title.innerText = `🍴 ${dish}`;

    // 删除整个菜品按钮
    const delDishBtn = document.createElement("button");
    delDishBtn.innerText = "🗑 删除菜品";
    delDishBtn.style.marginLeft = "10px";
    delDishBtn.onclick = async () => {
      await fetch(`/delete_dish/${dish}`, { method: "DELETE" });
      showToast(`已删除菜品 ${dish}`);
      listTemplates(); // ✅ 不重启相机
    };

    title.appendChild(delDishBtn);
    section.appendChild(title);

    const gallery = document.createElement("div");
    gallery.style.display = "flex";
    gallery.style.flexWrap = "wrap";

    data[dish].forEach(item => {
      const wrapper = document.createElement("div");
      wrapper.style.position = "relative";

      const img = document.createElement("img");
      img.src = `/templates_store/${dish}/${item.file}`;

      const btn = document.createElement("button");
      btn.innerText = "❌";
      btn.style.position = "absolute";
      btn.style.top = "0";
      btn.style.right = "0";
      btn.onclick = async () => {
        await fetch(`/delete_template/${dish}/${item.file}`, { method: "DELETE" });
        showToast("已删除图片");
        listTemplates();
      };

      wrapper.appendChild(img);
      wrapper.appendChild(btn);
      gallery.appendChild(wrapper);
    });

    section.appendChild(gallery);
    container.appendChild(section);
  });
}

// 实时识别
function startRecognition() {
  setInterval(async () => {
    if (!video.srcObject || video.videoWidth === 0) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(video, 0, 0);

    const blob = await new Promise(r => canvas.toBlob(r, "image/jpeg"));
    if (!blob) return;

    const formData = new FormData();
    formData.append("file", blob, "frame.jpg");

    try {
      const res = await fetch("/predict", { method: "POST", body: formData });
      const data = await res.json();
      if (data.best) {
        resultDiv.innerText = `识别结果: ${data.best.label} (${data.best.score.toFixed(3)})`;
      } else {
        resultDiv.innerText = data.error || "未识别";
      }
    } catch {
      resultDiv.innerText = "识别失败";
    }
  }, 2000);
}

// 阈值持久化：启动时加载
async function loadThreshold() {
  const res = await fetch("/get_threshold");
  const data = await res.json();
  document.getElementById("threshold").value = data.threshold;
  document.getElementById("thresholdValue").innerText = parseFloat(data.threshold).toFixed(2);
}

// 阈值滑块变化时自动更新
async function updateThreshold(val) {
  document.getElementById("thresholdValue").innerText = parseFloat(val).toFixed(2);
  const formData = new FormData();
  formData.append("value", val);
  await fetch("/set_threshold", { method: "POST", body: formData });
}

window.onload = () => {
  video = document.getElementById("video");
  resultDiv = document.getElementById("result");
  startCamera();
  listTemplates();
  startRecognition();
  loadThreshold();
  document.getElementById("threshold").addEventListener("input", e => updateThreshold(e.target.value));
};
