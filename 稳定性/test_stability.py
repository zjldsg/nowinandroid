import time
import os
from appium import webdriver
from appium.options.android import UiAutomator2Options  # 【关键】导入 Options 类
from selenium.common.exceptions import WebDriverException, NoSuchElementException

def run_stability_test():
    print(">>> 开始初始化 Appium 连接...")

    # 1. 定义基础配置参数
    desired_caps = {
        "platformName": "Android",
        "deviceName": "YourDeviceName",  # 【注意】请替换为你 'adb devices' 看到的设备名
        "app": r"C:\Users\mac\Downloads\nowinandroid-main\nowinandroid-main\app\build\outputs\apk\demo\debug\app-demo-debug.apk",
        "automationName": "UiAutomator2",
        "noReset": True,                 # 保持应用状态，不每次清除数据
        "newCommandTimeout": 600         # 防止长时间无操作导致断开
    }

    # 2. 【核心修复】将字典转换为 Options 对象
    options = UiAutomator2Options().load_capabilities(desired_caps)

    try:
        # 3. 使用 options 对象启动驱动
        driver = webdriver.Remote("http://localhost:4723", options=options)
        print(">>> Appium 连接成功！应用正在启动...")

        # 等待应用完全加载（根据网络情况可能需要更久）
        time.sleep(5)

        # --- 稳定性测试循环逻辑 ---
        loop_count = 0
        while True:
            loop_count += 1
            print(f"\n--- 第 {loop_count} 轮测试开始 ---")

            try:
                # 模拟用户操作：这里以 NowInAndroid 的常见结构为例
                # 尝试点击列表中的某个元素（假设是 RecyclerView 或列表项）
                # 如果找不到特定 ID，可以使用通用策略，例如点击屏幕中间
                print("尝试寻找并点击列表内容...")

                # 策略 A：尝试通过 ID 查找（如果知道具体的资源 ID）
                # driver.find_element(by="id", value="com.google.samples.apps.nowinandroid.demo.debug:id/...").click()

                # 策略 B：通用盲点策略（针对稳定性测试，不知道具体 ID 时使用）
                # 获取屏幕尺寸
                size = driver.get_window_size()
                width = size["width"]
                height = size["height"]

                # 在屏幕下半部分随机点击（通常是列表区域）
                x = width * 0.5
                y = height * 0.6
                driver.tap([(x, y)])
                time.sleep(1)

                # 模拟下滑刷新或浏览
                driver.swipe(width * 0.5, height * 0.8, width * 0.5, height * 0.2, 1000)
                time.sleep(1)

                # 模拟返回操作，测试页面栈稳定性
                driver.back()
                time.sleep(1)

                print("本轮操作完成，未检测到崩溃。")

            except Exception as e:
                # 捕获异常：可能是 Crash 或 ANR 导致元素丢失
                error_msg = str(e)
                print(f"!!! 检测到异常: {error_msg}")

                # 检查是否是 Session 丢失（意味着 App 崩了）
                if "session deleted" in error_msg or "no such session" in error_msg:
                    print("!!! 严重错误：App 进程可能已崩溃 (Session Lost)")
                    # 可以在这里添加重启逻辑，或者直接退出
                    break

                # 截图保存现场
                screenshot_name = f"crash_{int(time.time())}.png"
                driver.save_screenshot(screenshot_name)
                print(f"!!! 已保存截图到: {os.path.abspath(screenshot_name)}")

                # 尝试重新回到主页或重启（可选）
                try:
                    driver.activate_app("com.google.samples.apps.nowinandroid.demo.debug")
                except:
                    pass

            # 每一轮之间的休息时间
            time.sleep(2)

    except Exception as e:
        print(f"初始化失败: {e}")
    finally:
        # 测试结束后不要自动关闭 driver，方便你查看手机状态
        # driver.quit()
        print(">>> 脚本运行结束")

if __name__ == "__main__":
    run_stability_test()