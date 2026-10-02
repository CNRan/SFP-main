#!/usr/bin/env python3
"""校验仓库内所有 YAML 配置能否被正确解析。

CI 中由 .github/workflows/syntax-check.yml 调用；本地也可直接运行：

    pip install pyyaml
    python scripts/check_yaml.py

退出码：0 = 全部通过，1 = 存在无法解析的文件。
（空文件只提示、不算失败，避免正在编辑的配置把 CI 判红。）
"""

from __future__ import annotations

import pathlib
import sys

import yaml

# 需要校验的目录（相对仓库根目录）
TARGETS = ("src/main/resources", ".github/workflows")


def main() -> int:
    root = pathlib.Path(__file__).resolve().parent.parent
    failed: list[str] = []
    empty: list[str] = []
    checked = 0

    for target in TARGETS:
        base = root / target
        if not base.is_dir():
            print(f"[跳过] 目录不存在：{target}")
            continue

        for path in sorted(set(base.rglob("*.yml")) | set(base.rglob("*.yaml"))):
            rel = path.relative_to(root).as_posix()
            checked += 1
            try:
                text = path.read_text(encoding="utf-8")
            except OSError as exc:
                failed.append(rel)
                print(f"[FAIL] {rel}（读取失败：{exc}）")
                continue

            if not text.strip():
                empty.append(rel)
                print(f"[WARN] {rel}（空文件）")
                continue

            try:
                yaml.safe_load(text)
            except Exception as exc:  # noqa: BLE001 —— 仅用于汇报，保留原始错误信息
                failed.append(rel)
                print(f"[FAIL] {rel}")
                for line in str(exc).splitlines():
                    print(f"       {line}")
            else:
                print(f"[ OK ] {rel}")

    print()
    print(f"共校验 {checked} 个文件，失败 {len(failed)} 个，空文件 {len(empty)} 个。")
    if empty:
        print("提示：空 YAML 文件会被服务端/CI 忽略或报错，确认是否应删除或补内容：")
        for rel in empty:
            print(f"  - {rel}")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
