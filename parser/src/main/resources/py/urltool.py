import requests
import urllib.parse
import re
import base64


"""
https://github.com/chenhal/short_url
"""
headers = {
    'Cookie': 'SUB=',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36',
    'Referer': 'https://www.weibo.com',
    'Content-Type': 'application/x-www-form-urlencoded'
}


def get_short_url(long_url):
    url = "https://www.weibo.com/aj/v6/comment/add"

    payload = urllib.parse.urlencode({
        'mid': '5094736413852129',
        'content': long_url
    })
    response = requests.post(url, headers=headers, data=payload)

    try:
        # print(response.json())
        data = response.json()['data']['comment']
        short_url = re.search(r'(https?)://t.cn/\w+', data).group(0)
        comment_id = re.findall(r'comment_id="(.+\d)"', data)[-1]   # 评论id
        print('微博短链：' + short_url)
        del_comment(comment_id)  # 需要删除评论，可以取消该行注释
    except:
        print('失败')
        pass


# 删除评论
def del_comment(comment_id):
    url = 'https://www.weibo.com/aj/comment/del'

    payload = urllib.parse.urlencode({
        'mid': '微博mid',
        'cid': comment_id  # 评论id
    })
    response = requests.post(url, headers=headers, data=payload)
    try:
        if response.json()['code'] == '100000':
            print('评论已删除')
    except:
        pass


if __name__ == '__main__':
    # https://so.toutiao.com/search/jump?url=https%3A%2F%2Fblog.qaiu.top%2Farchives%2Fpydroidall&aid=4916&jtoken=297f06127cb010274213422b1967bdc2ae8469b627205941dc287173b58a2a8439ea0d813d24ada8780047d33f37d7e82c6a620760de1ca37640c1dc143b4e01
    # https://so.toutiao.com/search/jump?url=https%3A%2F%2Fblog.qaiu.top%2Farchives%2Fpydroidall1&aid=4916&jtoken=297f06127cb010274213422b1967bdc2ae8469b627205941dc287173b58a2a8439ea0d813d24ada8780047d33f37d7e82c6a620760de1ca37640c1dc143b4e01

    # 原始 URL
    url = "https://lz.qaiu.top/json/lz/i5vOm0xho2cj"

    # 将 URL 编码为 Base64
    encoded_url = base64.b64encode(url.encode("utf-8")).decode("utf-8")
    get_short_url('https://www.so.com/link?m=ewgUSYiFWXIoTybC3fJH8YoJy8y10iRquo6cazgINwWjTn3HvVJ92TrCJu0PmMUR0RMDfOAucP3wa4G8j64SrhNH9Z0Cr0PEyn9ASuvpkUGmAjjUEGJkO5%2BIDGWVrEkPHsL7UsoKO6%2BlT%2BD6r&ccc=' + encoded_url)
