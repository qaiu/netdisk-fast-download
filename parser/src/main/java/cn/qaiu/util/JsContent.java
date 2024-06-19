package cn.qaiu.util;

public interface JsContent {
    String ye123 = "/*\n" +
            "            https://statics.123pan.com/share-static/dist/umi.fb72555e.js\n" +
            "            eaefamemdead\n" +
            "            eaefameidldy\n" +
            "            _0x4f141a(1690439821|5790548|/b/api/share/download/info|web|3|1946841013) = 秘钥\n" +
            "                        \n" +
            "            _0x1e2592 1690439821 时间戳\n" +
            "            _0x48562f 5790548 随机码\n" +
            "            _0x1e37d5  /b/api/share/download/info\n" +
            "            _0x4e2d74 web\n" +
            "            _0x56f040 3\n" +
            "            _0x43bdc6 1946841013 加密时间HASH戳\n" +
            "                        \n" +
            "            >>>>\n" +
            "            _0x43bdc6=''['concat'](_0x1e2592, '-')['concat'](_0x48562f, '-')['concat'](_0x406c4e)\n" +
            "            加密时间HASH戳 = 时间戳-随机码-秘钥\n" +
            "            */\n" +
            "                        \n" +
            "            function _0x1b5d95(_0x278d1a) {\n" +
            "              var _0x839b57,\n" +
            "                _0x4ed4dc = arguments['length'] > 0x2 && void 0x0 !== arguments[0x2] ? arguments[0x2] : " +
            "0x8;\n" +
            "              if (0x0 === arguments['length'])\n" +
            "                return null;\n" +
            "              'object' === typeof _0x278d1a ? _0x839b57 = _0x278d1a : (0xa === ('' + _0x278d1a)" +
            "['length'] && (_0x278d1a = 0x3e8 * parseInt(_0x278d1a)),\n" +
            "                _0x839b57 = new Date(_0x278d1a));\n" +
            "              var _0xc5c54a = _0x278d1a + 0xea60 * new Date(_0x278d1a)['getTimezoneOffset']()\n" +
            "                , _0x3732dc = _0xc5c54a + 0x36ee80 * _0x4ed4dc;\n" +
            "              return _0x839b57 = new Date(_0x3732dc),\n" +
            "                {\n" +
            "                  'y': _0x839b57['getFullYear'](),\n" +
            "                  'm': _0x839b57['getMonth']() + 0x1 < 0xa ? '0' + (_0x839b57['getMonth']() + 0x1) : " +
            "_0x839b57['getMonth']() + 0x1,\n" +
            "                  'd': _0x839b57['getDate']() < 0xa ? '0' + _0x839b57['getDate']() : " +
            "_0x839b57['getDate'](),\n" +
            "                  'h': _0x839b57['getHours']() < 0xa ? '0' + _0x839b57['getHours']() : " +
            "_0x839b57['getHours'](),\n" +
            "                  'f': _0x839b57['getMinutes']() < 0xa ? '0' + _0x839b57['getMinutes']() : " +
            "_0x839b57['getMinutes']()\n" +
            "                };\n" +
            "            }\n" +
            "                        \n" +
            "                        \n" +
            "            function _0x4f141a(_0x4075b1) {\n" +
            "                        \n" +
            "              for (var _0x4eddcb = arguments['length'] > 0x1 && void 0x0 !== arguments[0x1] ? " +
            "arguments[0x1] : 0xa,\n" +
            "                     _0x2fc680 = function() {\n" +
            "                  for (var _0x515c63, _0x361314 = [], _0x4cbdba = 0x0; _0x4cbdba < 0x100; _0x4cbdba++) " +
            "{\n" +
            "                    _0x515c63 = _0x4cbdba;\n" +
            "                    for (var _0x460960 = 0x0; _0x460960 < 0x8; _0x460960++)\n" +
            "                      _0x515c63 = 0x1 & _0x515c63 ? 0xedb88320 ^ _0x515c63 >>> 0x1 : _0x515c63 >>> 0x1;" +
            "\n" +
            "                    _0x361314[_0x4cbdba] = _0x515c63;\n" +
            "                  }\n" +
            "                  return _0x361314;\n" +
            "                },\n" +
            "                     _0x4aed86 = _0x2fc680(),\n" +
            "                     _0x5880f0 = _0x4075b1,\n" +
            "                     _0x492393 = -0x1, _0x25d82c = 0x0;\n" +
            "                   _0x25d82c < _0x5880f0['length'];\n" +
            "                   _0x25d82c++)\n" +
            "                        \n" +
            "                _0x492393 = _0x492393 >>> 0x8 ^ _0x4aed86[0xff & (_0x492393 ^ _0x5880f0.charCodeAt" +
            "(_0x25d82c))];\n" +
            "              return _0x492393 = (-0x1 ^ _0x492393) >>> 0x0,\n" +
            "                _0x492393.toString(_0x4eddcb);\n" +
            "            }\n" +
            "                        \n" +
            "                        \n" +
            "            function getSign(_0x1e37d5) {\n" +
            "              var _0x4e2d74 = 'web';\n" +
            "              var _0x56f040 = 3;\n" +
            "              var _0x1e2592 = Math.round((new Date().getTime() + 0x3c * new Date().getTimezoneOffset() *" +
            " 0x3e8 + 28800000) / 0x3e8).toString();\n" +
            "              var key = 'a,d,e,f,g,h,l,m,y,i,j,n,o,p,k,q,r,s,t,u,b,c,v,w,s,z';\n" +
            "              var _0x48562f = Math['round'](0x989680 * Math['random']());\n" +
            "                        \n" +
            "              var _0x2f7dfc;\n" +
            "              var _0x35a889;\n" +
            "              var _0x36f983;\n" +
            "              var _0x3b043d;\n" +
            "              var _0x5bc73b;\n" +
            "              var _0x4b30b2;\n" +
            "              var _0x32399e;\n" +
            "              var _0x25d94e;\n" +
            "              var _0x373490;\n" +
            "              for (var _0x1c540f in (_0x2f7dfc = key.split(','),\n" +
            "                _0x35a889 = _0x1b5d95(_0x1e2592),\n" +
            "                _0x36f983 = _0x35a889['y'],\n" +
            "                _0x3b043d = _0x35a889['m'],\n" +
            "                _0x5bc73b = _0x35a889['d'],\n" +
            "                _0x4b30b2 = _0x35a889['h'],\n" +
            "                _0x32399e = _0x35a889['f'],\n" +
            "                _0x25d94e = [_0x36f983, _0x3b043d, _0x5bc73b, _0x4b30b2, _0x32399e].join(''),\n" +
            "                _0x373490 = [],\n" +
            "                _0x25d94e))\n" +
            "                _0x373490['push'](_0x2f7dfc[Number(_0x25d94e[_0x1c540f])]);\n" +
            "              var _0x43bdc6;\n" +
            "              var _0x406c4e;\n" +
            "              return _0x43bdc6 = _0x4f141a(_0x373490['join']('')),\n" +
            "                _0x406c4e = _0x4f141a(''['concat'](_0x1e2592, '|')['concat'](_0x48562f, '|')['concat']" +
            "(_0x1e37d5, '|')['concat'](_0x4e2d74, '|')['concat'](_0x56f040, '|')['concat'](_0x43bdc6)),\n" +
            "                [_0x43bdc6, ''['concat'](_0x1e2592, '-')['concat'](_0x48562f, '-')['concat'](_0x406c4e)" +
            "];\n" +
            "            }\n" +
            "                 ";
    String lz = "/**\n" +
            "             * 蓝奏云解析器js签名获取工具\n" +
            "             */\n" +
            "                        \n" +
            "            var signObj;\n" +
            "                        \n" +
            "                        \n" +
            "            var $, jQuery;\n" +
            "                        \n" +
            "            $ = jQuery = function () {\n" +
            "              return new jQuery.fn.init();\n" +
            "            }\n" +
            "                        \n" +
            "            jQuery.fn = jQuery.prototype = {\n" +
            "              init: function () {\n" +
            "                return {\n" +
            "                  focus: function (a) {\n" +
            "                        \n" +
            "                  },\n" +
            "                  keyup: function(a) {\n" +
            "                        \n" +
            "                  },\n" +
            "                  ajax: function (obj) {\n" +
            "                    signObj = obj\n" +
            "                  }\n" +
            "                        \n" +
            "                }\n" +
            "              },\n" +
            "                        \n" +
            "            }\n" +
            "                        \n" +
            "            jQuery.fn.init.prototype = jQuery.fn;\n" +
            "                        \n" +
            "                        \n" +
            "            // 伪装jquery.ajax函数获取关键数据\n" +
            "            $.ajax = function (obj) {\n" +
            "              signObj = obj\n" +
            "            }\n" +
            "                        \n" +
            "            var document = {\n" +
            "              getElementById: function (v) {\n" +
            "                return {\n" +
            "                  value: 'v'\n" +
            "                }\n" +
            "              },\n" +
            "            }";

}
