package cn.qaiu.util;

public interface JsContent {
    String ye123 = """
            /*
            https://statics.123pan.com/share-static/dist/umi.fb72555e.js
            eaefamemdead
            eaefameidldy
            _0x4f141a(1690439821|5790548|/b/api/share/download/info|web|3|1946841013) = 秘钥
                        
            _0x1e2592 1690439821 时间戳
            _0x48562f 5790548 随机码
            _0x1e37d5  /b/api/share/download/info
            _0x4e2d74 web
            _0x56f040 3
            _0x43bdc6 1946841013 加密时间HASH戳
                        
            >>>>
            _0x43bdc6=''['concat'](_0x1e2592, '-')['concat'](_0x48562f, '-')['concat'](_0x406c4e)
            加密时间HASH戳 = 时间戳-随机码-秘钥
            */
                        
            function _0x1b5d95(_0x278d1a) {
              var _0x839b57,
                _0x4ed4dc = arguments['length'] > 0x2 && void 0x0 !== arguments[0x2] ? arguments[0x2] : 0x8;
              if (0x0 === arguments['length'])
                return null;
              'object' === typeof _0x278d1a ? _0x839b57 = _0x278d1a : (0xa === ('' + _0x278d1a)['length'] && (_0x278d1a = 0x3e8 * parseInt(_0x278d1a)),
                _0x839b57 = new Date(_0x278d1a));
              var _0xc5c54a = _0x278d1a + 0xea60 * new Date(_0x278d1a)['getTimezoneOffset']()
                , _0x3732dc = _0xc5c54a + 0x36ee80 * _0x4ed4dc;
              return _0x839b57 = new Date(_0x3732dc),
                {
                  'y': _0x839b57['getFullYear'](),
                  'm': _0x839b57['getMonth']() + 0x1 < 0xa ? '0' + (_0x839b57['getMonth']() + 0x1) : _0x839b57['getMonth']() + 0x1,
                  'd': _0x839b57['getDate']() < 0xa ? '0' + _0x839b57['getDate']() : _0x839b57['getDate'](),
                  'h': _0x839b57['getHours']() < 0xa ? '0' + _0x839b57['getHours']() : _0x839b57['getHours'](),
                  'f': _0x839b57['getMinutes']() < 0xa ? '0' + _0x839b57['getMinutes']() : _0x839b57['getMinutes']()
                };
            }
                        
                        
            function _0x4f141a(_0x4075b1) {
                        
              for (var _0x4eddcb = arguments['length'] > 0x1 && void 0x0 !== arguments[0x1] ? arguments[0x1] : 0xa,
                     _0x2fc680 = function() {
                  for (var _0x515c63, _0x361314 = [], _0x4cbdba = 0x0; _0x4cbdba < 0x100; _0x4cbdba++) {
                    _0x515c63 = _0x4cbdba;
                    for (var _0x460960 = 0x0; _0x460960 < 0x8; _0x460960++)
                      _0x515c63 = 0x1 & _0x515c63 ? 0xedb88320 ^ _0x515c63 >>> 0x1 : _0x515c63 >>> 0x1;
                    _0x361314[_0x4cbdba] = _0x515c63;
                  }
                  return _0x361314;
                },
                     _0x4aed86 = _0x2fc680(),
                     _0x5880f0 = _0x4075b1,
                     _0x492393 = -0x1, _0x25d82c = 0x0;
                   _0x25d82c < _0x5880f0['length'];
                   _0x25d82c++)
                        
                _0x492393 = _0x492393 >>> 0x8 ^ _0x4aed86[0xff & (_0x492393 ^ _0x5880f0.charCodeAt(_0x25d82c))];
              return _0x492393 = (-0x1 ^ _0x492393) >>> 0x0,
                _0x492393.toString(_0x4eddcb);
            }
                        
                        
            function getSign(_0x1e37d5) {
              var _0x4e2d74 = 'web';
              var _0x56f040 = 3;
              var _0x1e2592 = Math.round((new Date().getTime() + 0x3c * new Date().getTimezoneOffset() * 0x3e8 + 28800000) / 0x3e8).toString();
              var key = 'a,d,e,f,g,h,l,m,y,i,j,n,o,p,k,q,r,s,t,u,b,c,v,w,s,z';
              var _0x48562f = Math['round'](0x989680 * Math['random']());
                        
              var _0x2f7dfc;
              var _0x35a889;
              var _0x36f983;
              var _0x3b043d;
              var _0x5bc73b;
              var _0x4b30b2;
              var _0x32399e;
              var _0x25d94e;
              var _0x373490;
              for (var _0x1c540f in (_0x2f7dfc = key.split(','),
                _0x35a889 = _0x1b5d95(_0x1e2592),
                _0x36f983 = _0x35a889['y'],
                _0x3b043d = _0x35a889['m'],
                _0x5bc73b = _0x35a889['d'],
                _0x4b30b2 = _0x35a889['h'],
                _0x32399e = _0x35a889['f'],
                _0x25d94e = [_0x36f983, _0x3b043d, _0x5bc73b, _0x4b30b2, _0x32399e].join(''),
                _0x373490 = [],
                _0x25d94e))
                _0x373490['push'](_0x2f7dfc[Number(_0x25d94e[_0x1c540f])]);
              var _0x43bdc6;
              var _0x406c4e;
              return _0x43bdc6 = _0x4f141a(_0x373490['join']('')),
                _0x406c4e = _0x4f141a(''['concat'](_0x1e2592, '|')['concat'](_0x48562f, '|')['concat'](_0x1e37d5, '|')['concat'](_0x4e2d74, '|')['concat'](_0x56f040, '|')['concat'](_0x43bdc6)),
                [_0x43bdc6, ''['concat'](_0x1e2592, '-')['concat'](_0x48562f, '-')['concat'](_0x406c4e)];
            }
                        
            """;
    String lz = """
            /**
             * 蓝奏云解析器js签名获取工具
             */
                        
            var signObj;
                        
                        
            var $, jQuery;
                        
            $ = jQuery = function () {
              return new jQuery.fn.init();
            }
                        
            jQuery.fn = jQuery.prototype = {
              init: function () {
                return {
                  focus: function (a) {
                        
                  },
                  keyup: function(a) {
                        
                  },
                  ajax: function (obj) {
                    signObj = obj
                  }
                        
                }
              },
                        
            }
                        
            jQuery.fn.init.prototype = jQuery.fn;
                        
                        
            // 伪装jquery.ajax函数获取关键数据
            $.ajax = function (obj) {
              signObj = obj
            }
                        
            var document = {
              getElementById: function (v) {
                return {
                  value: 'v'
                }
              },
            }
                        
            """;

}
