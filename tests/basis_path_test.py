# -*- coding: utf-8 -*-
"""
基本路径测试（Basis Path Testing）

被测单元与独立路径：
一、EmployeeServiceImpl.login  V(G) = 6
  P1 用户不存在                         -> 账号不存在
  P2 历史 MD5 密码且密码错误            -> 密码错误
  P3 历史 MD5 密码且密码正确            -> 登录成功并升级为 BCrypt
  P4 BCrypt 密码且密码正确              -> 登录成功
  P5 BCrypt 密码且密码错误              -> 密码错误
  P6 密码正确但账号被禁用               -> 账号被锁定

二、ShoppingCartServiceImpl.add / sub  V(G) = 3 + 3
  A1 购物车已存在该商品 -> 数量 +1，且不新增重复行
  A2 购物车不存在、dishId 非空 -> 插入菜品
  A3 购物车不存在、dishId 为空 -> 插入套餐（需套餐数据，可选）
  S1 购物车不存在 -> 直接返回，不报错
  S2 数量 > 1 -> 数量 -1
  S3 数量 = 1 -> 删除该行

说明：
- 用户端接口需要 authentication 请求头。为了不依赖微信登录，本脚本用本地 JWT 密钥
  自行签发一个 userId 的令牌（仅本地测试用）。
- 依赖标准库，无需安装第三方包。
- 登录的禁用账号路径需要一条 status=0 的员工数据，未提供时自动跳过。
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_USER_SECRET = "b4244bb9d0731b4974de1bd00c51ba5a082cab089f779c70cb7d086431252813"

results = []


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def make_user_token(user_id: int, secret: str, ttl_seconds: int = 7200) -> str:
    """按 JwtUtil 的 HS256 规则签发用户令牌"""
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"userId": user_id, "exp": int(time.time()) + ttl_seconds}
    signing_input = "{}.{}".format(
        b64url(json.dumps(header, separators=(",", ":")).encode("utf-8")),
        b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8")),
    )
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    return "{}.{}".format(signing_input, b64url(signature))


def http_request(base_url, method, path, body=None, headers=None, timeout=10):
    url = base_url.rstrip("/") + path
    data = None
    hdrs = {"Content-Type": "application/json;charset=utf-8"}
    if headers:
        hdrs.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return resp.status, parse_json(raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        return e.code, parse_json(raw)
    except Exception as e:
        return -1, {"code": -1, "msg": str(e)}


def parse_json(raw):
    try:
        return json.loads(raw) if raw else {}
    except ValueError:
        return {"code": -1, "msg": raw[:200]}


def record(case_id, name, path, expected, status, resp, ok):
    results.append({
        "case": case_id,
        "name": name,
        "path": path,
        "expected": expected,
        "http": status,
        "actual": "code={}, msg={}".format(resp.get("code"), resp.get("msg")),
        "ok": ok,
    })
    print("[{}] {} | {} | 期望: {} | 实际: HTTP {} code={} msg={}".format(
        "PASS" if ok else "FAIL", case_id, name, expected, status, resp.get("code"), resp.get("msg")))


def login(base_url, username, password):
    return http_request(base_url, "POST", "/admin/employee/login",
                        {"username": username, "password": password})


def get_admin_dish_id(base_url, token):
    status, resp = http_request(
        base_url, "GET", "/admin/dish/page?page=1&pageSize=1&status=1",
        headers={"token": token})
    if resp.get("code") != 1:
        return None
    records = (resp.get("data") or {}).get("records") or []
    return records[0]["id"] if records else None


def get_admin_setmeal_id(base_url, token):
    status, resp = http_request(
        base_url, "GET", "/admin/setmeal/page?page=1&pageSize=1&status=1",
        headers={"token": token})
    if resp.get("code") != 1:
        return None
    records = (resp.get("data") or {}).get("records") or []
    return records[0]["id"] if records else None


def test_login(base_url, args):
    print("\n===== 一、员工登录基本路径 =====")
    base_user = args.admin_user

    status, resp = login(base_url, "no_such_user_9527", "123456")
    record("P1", "用户不存在", "employee==null", "code=0 账号不存在",
           status, resp, resp.get("code") == 0 and "账号不存在" in str(resp.get("msg")))

    status, resp = login(base_url, base_user, "wrong_password_123")
    record("P2/P5", "密码错误（MD5 或 BCrypt）", "passwordMatched=false", "code=0 密码错误",
           status, resp, resp.get("code") == 0 and "密码错误" in str(resp.get("msg")))

    status, resp = login(base_url, base_user, args.admin_pass)
    token = ((resp.get("data") or {}).get("token")) if resp.get("code") == 1 else None
    record("P3/P4", "密码正确（首次 MD5 或已升级 BCrypt）", "passwordMatched=true", "code=1 返回 token",
           status, resp, resp.get("code") == 1 and bool(token))

    if token:
        status, resp2 = login(base_url, base_user, args.admin_pass)
        record("P4", "密码正确（升级后 BCrypt 再登录）", "BCrypt 校验通过", "code=1 返回 token",
               status, resp2, resp2.get("code") == 1 and bool((resp2.get("data") or {}).get("token")))

        status, resp5 = login(base_url, base_user, "wrong_password_456")
        record("P5", "密码错误（BCrypt 校验失败）", "BCrypt matches=false", "code=0 密码错误",
               status, resp5, resp5.get("code") == 0 and "密码错误" in str(resp5.get("msg")))

    if args.disabled_user and args.disabled_pass:
        status, resp3 = login(base_url, args.disabled_user, args.disabled_pass)
        record("P6", "账号被禁用", "status==DISABLE", "code=0 账号被锁定",
               status, resp3, resp3.get("code") == 0 and "账号被锁定" in str(resp3.get("msg")))
    else:
        print("[SKIP] P6 账号被禁用：未提供 --disabled-user/--disabled-pass 测试数据")
    return token


def cart_list(base_url, headers):
    status, resp = http_request(base_url, "GET", "/user/shoppingCart/list", headers=headers)
    return status, resp, (resp.get("data") or [])


def find_item(items, key, value):
    for item in items:
        if item.get(key) == value:
            return item
    return None


def test_cart(base_url, user_token, dish_id, setmeal_id):
    print("\n===== 二、购物车基本路径 =====")
    headers = {"authentication": user_token}

    # 前置：清空购物车（clean 单一路径）
    status, resp = http_request(base_url, "DELETE", "/user/shoppingCart/clean", headers=headers)
    record("CLEAN", "清空购物车", "单一路径", "code=1", status, resp, resp.get("code") == 1)

    if dish_id is None:
        print("[SKIP] 购物车用例：未获取到菜品 id，可用 --dish-id 指定")
        return

    # A2 新建菜品
    status, resp = http_request(base_url, "POST", "/user/shoppingCart/add",
                                {"dishId": dish_id}, headers=headers)
    status_l, resp_l, items = cart_list(base_url, headers)
    item = find_item(items, "dishId", dish_id)
    ok = resp.get("code") == 1 and item is not None and item.get("number") == 1
    record("A2", "购物车无该菜品时新增", "list为空 -> insert", "code=1 且数量=1",
           status, resp, ok)

    # A1 已存在再加一次
    status, resp = http_request(base_url, "POST", "/user/shoppingCart/add",
                                {"dishId": dish_id}, headers=headers)
    status_l, resp_l, items = cart_list(base_url, headers)
    same_items = [i for i in items if i.get("dishId") == dish_id]
    ok = resp.get("code") == 1 and len(same_items) == 1 and same_items[0].get("number") == 2
    record("A1", "购物车已有该菜品时数量+1且不重复插入", "list非空 -> update + return",
           "code=1 且只有1行、数量=2", status, resp, ok)

    # S2 数量>1 时减一
    status, resp = http_request(base_url, "POST", "/user/shoppingCart/sub",
                                {"dishId": dish_id}, headers=headers)
    status_l, resp_l, items = cart_list(base_url, headers)
    item = find_item(items, "dishId", dish_id)
    ok = resp.get("code") == 1 and item is not None and item.get("number") == 1
    record("S2", "数量>1 时减一", "number-1 > 0 -> update", "code=1 且数量=1",
           status, resp, ok)

    # S3 数量=1 时再减，删除该行
    status, resp = http_request(base_url, "POST", "/user/shoppingCart/sub",
                                {"dishId": dish_id}, headers=headers)
    status_l, resp_l, items = cart_list(base_url, headers)
    ok = resp.get("code") == 1 and find_item(items, "dishId", dish_id) is None
    record("S3", "数量=1 时减一", "number<=0 -> delete", "code=1 且记录被删除",
           status, resp, ok)

    # S1 购物车为空时减一
    status, resp = http_request(base_url, "POST", "/user/shoppingCart/sub",
                                {"dishId": dish_id}, headers=headers)
    ok = resp.get("code") == 1
    record("S1", "购物车无该商品时减一", "list为空 -> return", "code=1 且不报错",
           status, resp, ok)

    # A3 新增套餐（可选）
    if setmeal_id is not None:
        status, resp = http_request(base_url, "POST", "/user/shoppingCart/add",
                                    {"setmealId": setmeal_id}, headers=headers)
        status_l, resp_l, items = cart_list(base_url, headers)
        item = find_item(items, "setmealId", setmeal_id)
        ok = resp.get("code") == 1 and item is not None and item.get("number") == 1
        record("A3", "新增套餐", "dishId为空 -> 查套餐并 insert", "code=1 且数量=1",
               status, resp, ok)
    else:
        print("[SKIP] A3 新增套餐：未获取到套餐数据")

    # 清理测试数据
    http_request(base_url, "DELETE", "/user/shoppingCart/clean", headers=headers)


def main():
    parser = argparse.ArgumentParser(description="呀哈兔便当屋 - 基本路径接口测试")
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-pass", default="123456")
    parser.add_argument("--user-id", type=int, default=9999)
    parser.add_argument("--user-secret", default=os.getenv("SKY_JWT_USER_SECRET", DEFAULT_USER_SECRET))
    parser.add_argument("--dish-id", type=int, default=None)
    parser.add_argument("--setmeal-id", type=int, default=None)
    parser.add_argument("--disabled-user", default=None)
    parser.add_argument("--disabled-pass", default=None)
    args = parser.parse_args()

    print("被测服务：{}".format(args.base_url))
    status, health = http_request(args.base_url, "GET", "/user/shop/status")
    if status != 200:
        print("服务不可用：HTTP {} {}".format(status, health))
        sys.exit(2)

    admin_token = test_login(args.base_url, args)

    dish_id = args.dish_id
    setmeal_id = args.setmeal_id
    if admin_token:
        dish_id = dish_id or get_admin_dish_id(args.base_url, admin_token)
        setmeal_id = setmeal_id or get_admin_setmeal_id(args.base_url, admin_token)

    user_token = make_user_token(args.user_id, args.user_secret)
    test_cart(args.base_url, user_token, dish_id, setmeal_id)

    passed = sum(1 for r in results if r["ok"])
    total = len(results)
    print("\n===== 汇总：{}/{} 通过 =====".format(passed, total))
    for r in results:
        if not r["ok"]:
            print("失败：{} {} | 期望 {} | 实际 {}".format(r["case"], r["name"], r["expected"], r["actual"]))
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
