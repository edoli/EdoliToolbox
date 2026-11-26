import { spawnSync } from "node:child_process";

const device = JSON.parse(process.env.EDOL_ANDROID_DEVICE ?? "null");
if (!device?.serial) {
    console.log("No Android device selected.");
    process.exit(0);
}

function runAdb(...args) {
    const result = spawnSync("adb", ["-s", device.serial, ...args], { stdio: "inherit" });
    if (result.error) {
        console.error(result.error.message);
        process.exit(1);
    }
    if (result.status !== 0) {
        process.exit(result.status ?? 1);
    }
}

runAdb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk");
runAdb(
    "shell", "am", "start", "-S",
    "-a", "android.intent.action.MAIN",
    "-c", "android.intent.category.LAUNCHER",
    "-n", "kr.co.edoli.edolitool/.MainActivity",
);
