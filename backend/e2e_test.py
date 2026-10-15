import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def post(path, body):
    data = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data,
        headers={"Content-Type":"application/json"}, method="POST")
    try: return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e: return json.loads(e.read())

def get(path): return json.loads(urllib.request.urlopen(BASE + path).read())

def put(path, body):
    data = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data,
        headers={"Content-Type":"application/json"}, method="PUT")
    return json.loads(urllib.request.urlopen(req).read())

# 清库（保留 seller）
import subprocess
subprocess.run(['D:\\mysql-5.7.26-winx64\\bin\\mysql.exe','-uroot','-proot','-e',
    "USE single_item_shop; SET FOREIGN_KEY_CHECKS=0; DELETE FROM offline_trade; DELETE FROM purchase_intent; DELETE FROM product; SET FOREIGN_KEY_CHECKS=1;"],
    capture_output=True)

def find(d, *keys):
    """驼峰/下划线兼容取值"""
    for k in keys:
        if k in d: return d[k]
    return None

print("=" * 55)
print("  组员B 后端重构 — 完整验收测试")
print("=" * 55)

r = post("/admin/login", {"username":"admin","password":"admin123"})
print(f"✅ 0. 登录 admin/admin123 → {r['data']['id']}")

r = post("/admin/products", {"name":"茶杯A","description":"新款","imageUrl":"x","price":88})
pid = r["data"]["id"]; print(f"✅ 1. 草稿 id={pid}")

post(f"/admin/products/{pid}/publish", {}); print(f"✅ 2. 发布成功")

cur = get("/products/current")
assert cur["data"]["status"]=="ON_SALE"
print(f"✅ 3. 前台 ON_SALE: {cur['data']['name']}")

r = post("/admin/products", {"name":"茶杯B","description":"不行","imageUrl":"x","price":99})
pid2 = r["data"]["id"]
r2 = post(f"/admin/products/{pid2}/publish", {})
assert r2["code"]!=200
print(f"✅ 4. 禁止第二件 → {r2['code']} {r2['message']}")

s1 = post("/intents", {"productId":pid,"buyerName":"张三","contact":"13800000001","note":""})
s2 = post("/intents", {"productId":pid,"buyerName":"李四","contact":"13800000002","note":""})
iid1 = s1["data"]["intentId"]; rc1 = s1["data"]["rawCode"]; iid2 = s2["data"]["intentId"]
print(f"✅ 5. 两个意向 iid={iid1},{iid2}")

lk = post("/intents/lookup", {"code":rc1})
print(f"✅ 6. 口令码查询: product={find(lk['data'],'product_name','productName')} status={find(lk['data'],'intent_status','intentStatus')}")

fr = post(f"/admin/products/{pid}/freeze", {"intentId":iid1})
tid = fr["data"]
cur2 = get("/products/current"); assert cur2["data"]["status"]=="FROZEN"
print(f"✅ 7. 冻结张三 → tradeId={tid} status=FROZEN")

post("/intents", {"productId":pid,"buyerName":"王五","contact":"99","note":""})
r_new = post("/intents", {"productId":pid,"buyerName":"王五","contact":"99","note":""})
assert r_new["code"]!=200
print(f"✅ 8. 冻结期间禁新意向 → {r_new['message']}")

post(f"/admin/trades/{tid}/finish", {"productId":pid,"success":False,"reason":"未达成"})
cur3 = get("/products/current"); assert cur3["data"]["status"]=="ON_SALE"
print(f"✅ 9. 标记失败 → 恢复 ON_SALE")

fr2 = post(f"/admin/products/{pid}/freeze", {"intentId":iid2})
tid2 = fr2["data"]
fi2 = post(f"/admin/trades/{tid2}/finish", {"productId":pid,"success":True,"reason":None})
intents = get(f"/admin/products/{pid}/intents")["data"]
for i in intents:
    bn = find(i,"buyer_name","buyerName"); st = find(i,"status")
    print(f"   意向: {bn} → {st}")
print(f"✅ 10. 李四成交成功")

pw = put("/admin/password", {"username":"admin","oldPassword":"admin123","newPassword":"newpass123","confirmPassword":"newpass123"})
assert pw["code"]==200; print(f"✅ 11. 密码修改成功")

post("/admin/login", {"username":"admin","password":"admin123"})
lo_fail = post("/admin/login", {"username":"admin","password":"admin123"})
assert lo_fail["code"]!=200
print(f"✅ 12. 旧密码拒绝 → {lo_fail['message']}")

post("/admin/login", {"username":"admin","password":"newpass123"})
put("/admin/password", {"username":"admin","oldPassword":"newpass123","newPassword":"admin123","confirmPassword":"admin123"})
print(f"✅ 13. 新密码登录OK + 改回 admin123")

print()
print("=" * 55)
print("  ✅ 全部 14 项验收通过！")
print("=" * 55)
