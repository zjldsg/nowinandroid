#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Nowinandroid APK 静态分析脚本 (适配 Androguard 4.x)
功能：提取清单信息、组件统计、证书分析及生成调用图(CG)
依赖库：andrograph (pip install androguard)
"""

import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime

# ================= 核心导入区 =================
try:
    # 尝试导入 Androguard 4.x 新路径
    from androguard.core.apk import APK
    from androguard.core.analysis.analysis import Analysis
except ImportError:
    print("[!] 错误：未检测到 androguard 库。")
    print("    请运行命令安装: pip install androguard")
    sys.exit(1)
# ==============================================

# ================= 配置区域 =================
# TODO: 请在此处修改为你实际的 APK 文件路径
APK_PATH = r"C:\Users\mac\Downloads\nowinandroid-main\nowinandroid-main\app\build\outputs\apk\demo\debug\app-demo-debug.apk"
# ===========================================


def check_is_exported(apk_obj, component_name, tag_type):
    """
    手动检查组件是否导出 (兼容 Androguard 4.x)
    :param apk_obj: APK 对象
    :param component_name: 组件全名 (e.g., com.example.MainActivity)
    :param tag_type: 标签类型 (activity, service, receiver, provider)
    :return: True if exported
    """
    try:
        manifest_xml = apk_obj.get_xml('AndroidManifest.xml')
        root = manifest_xml.getroot()
        package_name = root.attrib.get('package', '')

        for item in root.findall(f'.//{tag_type}'):
            name_attr = item.get('{http://schemas.android.com/apk/res/android}name', '')

            # 处理名称匹配 (处理 .Activity 或 全名 的情况)
            is_match = False
            if name_attr == component_name:
                is_match = True
            elif name_attr.startswith('.') and (package_name + name_attr) == component_name:
                is_match = True

            if is_match:
                exported_val = item.get('{http://schemas.android.com/apk/res/android}exported')
                # Android 默认规则：如果有 intent-filter 且未显式声明 exported=false，通常视为导出
                # 这里简化逻辑：只要显式写了 true，或者没写但有 filter (复杂情况简化处理)
                if exported_val and exported_val.lower() == 'true':
                    return True
                # 注意：如果 exported 属性不存在，逻辑较复杂，这里仅检测显式标记
                # 若需更严谨，需结合 intent-filter 判断，但作为静态扫描脚本，显式标记最重要
                return False
    except Exception as e:
        pass
    return False


def analyze_apk(file_path):
    if not os.path.exists(file_path):
        print(f"[!] 错误：找不到文件 {file_path}")
        return

    print(f"[*] 正在加载 APK: {os.path.basename(file_path)}")
    try:
        a = APK(file_path)
    except Exception as e:
        print(f"[!] 无法解析 APK: {e}")
        return

    if not a.is_valid_APK():
        print("[!] 无效的 APK 文件")
        return

    print(f"[*] 包名 (Package): {a.get_package()}")
    print(f"[*] 应用名称 (Name): {a.get_app_name()}")
    print(f"[*] 版本号 (Version): {a.get_androidversion_code()}")
    print(f"[*] 最小 SDK (MinSDK): {a.get_min_sdk_version()}")
    print(f"[*] 目标 SDK (TargetSDK): {a.get_target_sdk_version()}")

    # --- 证书信息 ---
    print("\n" + "=" * 50)
    print("🔒 签名与证书信息")
    print("=" * 50)
    certs = a.get_certificates_der_v3() or a.get_certificates_der_v2() or a.get_certificates()
    if certs:
        for cert in certs:
            # 尝试获取 SHA-256 指纹 (Hex)
            import hashlib
            digest = hashlib.sha256(cert).hexdigest()
            print(f"[+] 证书 SHA-256: {digest[:20]}... (共 {len(digest)} 字符)")
            print(f"    证书长度: {len(cert)} bytes")
    else:
        print("[-] 未找到签名证书信息")

    # --- 组件统计 ---
    print("\n" + "=" * 50)
    print("🧩 四大组件统计")
    print("=" * 50)
    activities = a.get_activities()
    services = a.get_services()
    receivers = a.get_receivers()
    providers = a.get_providers()

    print(f"  Activities: {len(activities)}")
    print(f"  Services:   {len(services)}")
    print(f"  Receivers:  {len(receivers)}")
    print(f"  Providers:  {len(providers)}")

    # --- 详细列表 (修复后的逻辑) ---
    print("\n[Activity 列表]:")
    for act in activities:
        is_exp = check_is_exported(a, act, 'activity')
        flag = " [Exported]" if is_exp else ""
        print(f"  - {act}{flag}")

    print("\n[Service 列表]:")
    for svc in services:
        is_exp = check_is_exported(a, svc, 'service')
        flag = " [Exported]" if is_exp else ""
        print(f"  - {svc}{flag}")

    print("\n[BroadcastReceiver 列表]:")
    for rcv in receivers:
        is_exp = check_is_exported(a, rcv, 'receiver')
        flag = " [Exported]" if is_exp else ""
        print(f"  - {rcv}{flag}")

    # --- 权限分析 ---
    print("\n" + "=" * 50)
    print("🛡️ 权限分析")
    print("=" * 50)
    perms = a.get_permissions()
    if perms:
        for p in sorted(perms):
            print(f"  - {p}")
    else:
        print("  (无特殊权限申请)")

    # --- 调用图 (Call Graph) 生成 ---
    print("\n" + "=" * 50)
    print("🕸️ 正在生成调用图 (Call Graph)...")
    print("=" * 50)
    try:
        # 初始化分析引擎
        d, dx = Analysis([a])

        # 简单的 CG 导出逻辑
        cg = dx.get_call_graph()

        # 导出为 Dot 格式以便可视化
        output_dot = os.path.join(os.path.dirname(file_path), "call_graph.dot")
        with open(output_dot, "w", encoding='utf-8') as f:
            f.write(cg.to_dot())

        print(f"[+] 调用图已保存至: {output_dot}")
        print("    提示: 可使用 Graphviz 或在线工具查看 .dot 文件")

    except Exception as e:
        print(f"[!] 生成调用图失败: {e}")
        print("    可能是 APK 过大导致内存不足，或包含混淆代码。")

    print("\n[*] 分析完成！")


if __name__ == "__main__":
    analyze_apk(APK_PATH)