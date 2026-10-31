// Renders feature-graphic.html to feature-graphic.png at 1024x500 in headless Chrome.
// Usage, from the repository root: node art/feature-graphic.mjs

import { writeFileSync } from "node:fs";
import { serve, launch } from "../web/tests/ui/chrome.mjs";

const server = await serve();
const page = await launch();
try {
    await page.send("Emulation.setDeviceMetricsOverride", { width: 1024, height: 500, deviceScaleFactor: 1, mobile: false });
    await page.send("Page.navigate", { url: server.url.replace(/web\/$/, "art/feature-graphic.html") });
    await page.waitFor("document.fonts.ready.then(() => document.fonts.size >= 2 && document.querySelector('.icon').complete)");
    await page.sleep(300);
    const shot = await page.send("Page.captureScreenshot", { clip: { x: 0, y: 0, width: 1024, height: 500, scale: 1 } });
    writeFileSync(new URL("feature-graphic.png", import.meta.url), Buffer.from(shot.data, "base64"));
} finally {
    await page.close();
    server.close();
}
