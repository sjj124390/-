"""Integration checks against an EMPTY disposable Docker demo database."""
import argparse
import json
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:5500')
    parser.add_argument('--allow-write', action='store_true', required=True,
                        help='Allow creating demo records in an EMPTY test database')
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    count = 0

    def check(value, message):
        nonlocal count
        if not value:
            raise AssertionError(message)
        count += 1
        print('PASS %02d %s' % (count, message), flush=True)

    def api(path, body=None):
        request = urllib.request.Request(base + '/api' + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return json.load(response)

    def ok(path, body=None):
        response = api(path, body)
        if response.get('code') != 200:
            raise AssertionError('%s: %s' % (path, response))
        return response.get('data')

    for attempt in range(60):
        try:
            login = api('/seller/login', {'username': 'admin', 'password': 'admin123'})
            if login.get('code') == 200:
                break
        except (OSError, ValueError):
            pass
        time.sleep(2)
    else:
        raise RuntimeError('Backend did not become ready through Nginx within 120 seconds')

    check(bool(login['data']['token']), 'Nginx -> backend -> database login')
    with urllib.request.urlopen(base + '/index.html', timeout=10) as response:
        check(response.status == 200, 'frontend HTML is served')
    with urllib.request.urlopen(base + '/js/config.js', timeout=10) as response:
        check("'/api'" in response.read().decode(), 'frontend uses same-origin /api')
    check(api('/seller/login', {'username':'admin', 'password':'wrong'})['code'] != 200,
          'incorrect password rejected')
    history = ok('/product/list')
    if history:
        raise RuntimeError('Use a new disposable Compose project; refusing to modify existing data')

    product = ok('/product/add', {'name':'Docker smoke product', 'description':'CI disposable item',
                                 'price':88.5, 'imageUrl':''})
    pid = product['id']
    check(product['status'] == 0, 'product published')
    check(ok('/product/detail')['id'] == pid, 'buyer can view current product')
    check(api('/product/add', {'name':'Second item', 'description':'Should reject',
                              'price':99, 'imageUrl':''})['code'] != 200,
          'second active product rejected')
    check(api('/order/submit', {'productId':pid, 'buyerName':'',
                               'buyerPhone':'13800000000'})['code'] != 200,
          'empty buyer name rejected')
    order = ok('/order/submit', {'productId':pid, 'buyerName':'CI buyer',
                                'buyerPhone':'13800000000', 'remark':'test only'})
    check(len(order['code']) == 32, 'purchase returns 32-character query code')
    check(ok('/intents/lookup', {'code':order['code']}) is not None, 'query code lookup works')
    check(api('/intents/lookup', {'code':'0'*32})['code'] != 200, 'wrong query code rejected')
    check(len(ok('/order/list')) == 1, 'seller can list purchase intent')

    # Exercise transaction freeze/failure via native API before final success.
    trade = ok('/admin/products/%s/freeze' % pid, {'intentId':order['intentId']})
    check(ok('/product/detail')['status'] == 1, 'selected intent freezes product')
    check(api('/order/submit', {'productId':pid, 'buyerName':'Late buyer',
                               'buyerPhone':'13800000001'})['code'] != 200,
          'frozen product rejects new intents')
    ok('/admin/trades/%s/finish' % trade, {'productId':pid, 'success':False, 'reason':'CI retry'})
    check(ok('/product/detail')['status'] == 0, 'failed transaction restores product')
    second = ok('/order/submit', {'productId':pid, 'buyerName':'Final buyer',
                                 'buyerPhone':'13800000002', 'remark':'test only'})
    ok('/order/updateStatus', {'orderId':second['intentId'], 'status':1})
    check(ok('/product/list')[0]['status'] == 2, 'completed transaction marks product sold')
    orders = ok('/order/list')
    check(any(row['id'] == second['intentId'] and row['status'] == 1 for row in orders),
          'completed intent is visible in seller history')
    print('Docker integration smoke test passed: %s checks' % count, flush=True)


if __name__ == '__main__':
    main()
