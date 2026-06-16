import time
import csv
import subprocess
from appium import webdriver
from appium.options.android import UiAutomator2Options

# ================= 配置区域 =================
APP_PACKAGE = "com.google.samples.apps.nowinandroid.demo.debug"
APP_ACTIVITY = "com.google.samples.apps.nowinandroid.MainActivity"
APK_PATH = r"C:\Users\mac\Downloads\nowinandroid-main\nowinandroid-main\app\build\outputs\apk\demo\debug\app-demo-debug.apk"

DURATION_SECONDS = 30
INTERVAL_SECONDS = 2
OUTPUT_FILE = "perf_data.csv"
# ===========================================

def get_pid(package_name):
    """获取应用 PID"""
    cmd = f'adb shell pidof {package_name}'
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.stdout.strip()

def get_cpu_usage(pid):
    """
    通过读取 /proc/pid/stat 计算 CPU 使用率
    这是最稳健的方法，特别适用于雷电模拟器
    """
    try:
        # 第一次采样
        stat1 = subprocess.run(f'adb shell cat /proc/{pid}/stat', shell=True, capture_output=True, text=True).stdout.split()
        uptime1 = float(subprocess.run('adb shell cat /proc/uptime', shell=True, capture_output=True, text=True).stdout.split()[0])

        utime1 = int(stat1[13])
        stime1 = int(stat1[14])

        # 等待一小段时间（例如 0.5秒 - 1秒）来计算差值
        time.sleep(1)

        # 第二次采样
        stat2 = subprocess.run(f'adb shell cat /proc/{pid}/stat', shell=True, capture_output=True, text=True).stdout.split()
        uptime2 = float(subprocess.run('adb shell cat /proc/uptime', shell=True, capture_output=True, text=True).stdout.split()[0])

        utime2 = int(stat2[13])
        stime2 = int(stat2[14])

        # 计算 CPU 使用率
        total_time = (utime2 + stime2) - (utime1 + stime1)
        elapsed_time = uptime2 - uptime1

        if elapsed_time > 0:
            cpu_percent = (total_time / elapsed_time) 
            return round(cpu_percent, 1)
        else:
            return 0.0

    except Exception as e:
        print(f"[Debug] CPU 计算出错: {e}")
        return 0.0

def get_memory_usage(package_name):
    """获取内存占用 (PSS)"""
    cmd = f'adb shell dumpsys meminfo {package_name} | findstr "TOTAL"'
    output = subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout.strip()
    if output and ":" in output:
        try:
            return output.split(":")[1].strip().split()[0]
        except:
            return "N/A"
    return "N/A"

def run_performance_test():
    options = UiAutomator2Options()
    options.load_capabilities({
        "platformName": "Android",
        "deviceName": "emulator-5554",  # 雷电通常端口是 5555 或 5554，Appium 会自动识别
        "app": APK_PATH,
        "appPackage": APP_PACKAGE,
        "appActivity": APP_ACTIVITY,
        "automationName": "UiAutomator2"
    })

    print("正在初始化 Appium Driver...")
    driver = webdriver.Remote("http://127.0.0.1:4723", options=options)
    print(f"成功连接到设备并启动应用: {APP_PACKAGE}")

    # 等待应用加载
    time.sleep(3)

    data_rows = []
    print(f"开始采集性能数据，持续 {DURATION_SECONDS} 秒...")

    try:
        start_time = time.time()
        while (time.time() - start_time) < DURATION_SECONDS:
            timestamp = time.strftime("%H:%M:%S")
            pid = get_pid(APP_PACKAGE)

            if not pid:
                print("未找到应用进程，可能已崩溃或未启动")
                continue

            # 获取数据
            cpu_val = get_cpu_usage(pid)
            mem_val = get_memory_usage(APP_PACKAGE)

            row = [timestamp, f"{cpu_val}%", f"{mem_val} KB"]
            data_rows.append(row)
            print(f"[{timestamp}] CPU: {cpu_val}% | MEM: {mem_val} KB")

            time.sleep(INTERVAL_SECONDS)

    except Exception as e:
        print(f"采集过程中出错: {e}")
    finally:
        with open(OUTPUT_FILE, mode='w', newline='', encoding='utf-8') as file:
            writer = csv.writer(file)
            writer.writerow(["Timestamp", "CPU (%)", "Memory (KB)"])
            writer.writerows(data_rows)

        print(f"\n测试结束！数据已保存至: {OUTPUT_FILE}")
        driver.quit()

if __name__ == "__main__":
    run_performance_test()