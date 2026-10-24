import * as R from "./repo.js";
import { start } from "./ui.js";

if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});

R.load().then(start).catch(err => {
    document.getElementById("app").innerHTML =
        `<div class="loading">Не удалось загрузить словарь. Проверьте интернет и откройте снова.<br><small>${err}</small></div>`;
});
