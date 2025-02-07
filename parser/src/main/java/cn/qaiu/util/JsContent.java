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
                  },
                  val: function(a) {
                        
                  },
                        
                }
              },
                        
            }
                        
            jQuery.fn.init.prototype = jQuery.fn;
                        
                        
            $.ajax = function (obj) {
              signObj = obj
            }
                        
            var document = {
              getElementById: function (v) {
                return {
                  value: 'v',
                  style: {
                    display: ''
                  },
                  addEventListener: function() {}
                }
              },
            }
            
            var window = {location: {}}
            """;

    String kwSignString = """
            function encrypt(str, pwd) {
                if (pwd == null || pwd.length <= 0) {
                    return null;
                }
                var prand = "";
                for (var i = 0; i < pwd.length; i++) {
                    prand += pwd.charCodeAt(i).toString();
                }
                var sPos = Math.floor(prand.length / 5);
                var mult = parseInt(prand.charAt(sPos) + prand.charAt(sPos * 2) + prand.charAt(sPos * 3) + prand.charAt(sPos * 4) + prand.charAt(sPos * 5));
                var incr = Math.ceil(pwd.length / 2);
                var modu = Math.pow(2, 31) - 1;
                if (mult < 2) {
                    return null;
                }
                var salt = Math.round(Math.random() * 1000000000) % 100000000;
                prand += salt;
                var flag = 1;
                while (prand.length > 10) {
                    prand = (parseInt(prand.substring(0, 10)) + (flag ?parseFloat(prand.substring(10, prand.length)) : parseInt(prand.substring(10, prand.length)) )).toString();
                    flag = 0;
                }
                prand = (mult * prand + incr) % modu;
                var enc_chr = "";
                var enc_str = "";
                for (var i = 0; i < str.length; i++) {
                    enc_chr = parseInt(str.charCodeAt(i) ^ Math.floor((prand / modu) * 255));
                    if (enc_chr < 16) {
                        enc_str += "0" + enc_chr.toString(16);
                    } else enc_str += enc_chr.toString(16);
                    prand = (mult * prand + incr) % modu;
                }
                salt = salt.toString(16);
                while (salt.length < 8) salt = "0" + salt;
                enc_str += salt;
                return enc_str;
            }
            """;

    String mgJS = """
                        
                        
                var XO, qO = {
                    exports: {}
                }, JO = {
                    exports: {}
                };
                        
                var qb = globalThis = this
                        
                JO.exports = (XO = XO || function (e, t) {
                    var n;
                    if ("undefined" != typeof window && window.crypto && (n = window.crypto),
                    "undefined" != typeof self && self.crypto && (n = self.crypto),
                    "undefined" != typeof globalThis && globalThis.crypto && (n = globalThis.crypto),
                    !n && "undefined" != typeof window && window.msCrypto && (n = window.msCrypto),
                    !n && void 0 !== qb && qb.crypto && (n = qb.crypto),
                        !n)
                        try {
                            n = require("crypto")
                        } catch (g) {
                        }
                    var r = function () {
                        
                        return Math.floor(Math.random() * 0xFFFFFFFF + 0)
                    }
                        , o = Object.create || function () {
                        function e() {
                        }
                        
                        return function (t) {
                            var n;
                            return e.prototype = t,
                                n = new e,
                                e.prototype = null,
                                n
                        }
                    }()
                        , a = {}
                        , i = a.lib = {}
                        , l = i.Base = {
                        extend: function (e) {
                            var t = o(this);
                            return e && t.mixIn(e),
                            t.hasOwnProperty("init") && this.init !== t.init || (t.init = function () {
                                    t.$super.init.apply(this, arguments)
                                }
                            ),
                                t.init.prototype = t,
                                t.$super = this,
                                t
                        },
                        create: function () {
                            var e = this.extend();
                            return e.init.apply(e, arguments),
                                e
                        },
                        init: function () {
                        },
                        mixIn: function (e) {
                            for (var t in e)
                                e.hasOwnProperty(t) && (this[t] = e[t]);
                            e.hasOwnProperty("toString") && (this.toString = e.toString)
                        },
                        clone: function () {
                            return this.init.prototype.extend(this)
                        }
                    }
                        , s = i.WordArray = l.extend({
                        init: function (e, n) {
                            e = this.words = e || [],
                                this.sigBytes = n != t ? n : 4 * e.length
                        },
                        toString: function (e) {
                            return (e || c).stringify(this)
                        },	
                        concat: function (e) {
                            var t = this.words
                                , n = e.words
                                , r = this.sigBytes
                                , o = e.sigBytes;
                            if (this.clamp(),
                            r % 4)
                                for (var a = 0; a < o; a++) {
                                    var i = n[a >>> 2] >>> 24 - a % 4 * 8 & 255;
                                    t[r + a >>> 2] |= i << 24 - (r + a) % 4 * 8
                                }
                            else
                                for (var l = 0; l < o; l += 4)
                                    t[r + l >>> 2] = n[l >>> 2];
                            return this.sigBytes += o,
                                this
                        },
                        clamp: function () {
                            var t = this.words
                                , n = this.sigBytes;
                            t[n >>> 2] &= 4294967295 << 32 - n % 4 * 8,
                                t.length = e.ceil(n / 4)
                        },
                        clone: function () {
                            var e = l.clone.call(this);
                            return e.words = this.words.slice(0),
                                e
                        },
                        random: function (e) {
                            for (var t = [], n = 0; n < e; n += 4)
                                t.push(r());
                            return new s.init(t, e)
                        }
                    })
                        , u = a.enc = {}
                        , c = u.Hex = {
                        stringify: function (e) {
                            for (var t = e.words, n = e.sigBytes, r = [], o = 0; o < n; o++) {
                                var a = t[o >>> 2] >>> 24 - o % 4 * 8 & 255;
                                r.push((a >>> 4).toString(16)),
                                    r.push((15 & a).toString(16))
                            }
                            return r.join("")
                        },
                        parse: function (e) {
                            for (var t = e.length, n = [], r = 0; r < t; r += 2)
                                n[r >>> 3] |= parseInt(e.substr(r, 2), 16) << 24 - r % 8 * 4;
                            return new s.init(n, t / 2)
                        }
                    }
                        , d = u.Latin1 = {
                        stringify: function (e) {
                            for (var t = e.words, n = e.sigBytes, r = [], o = 0; o < n; o++) {
                                var a = t[o >>> 2] >>> 24 - o % 4 * 8 & 255;
                                r.push(String.fromCharCode(a))
                            }
                            return r.join("")
                        },
                        parse: function (e) {
                            for (var t = e.length, n = [], r = 0; r < t; r++)
                                n[r >>> 2] |= (255 & e.charCodeAt(r)) << 24 - r % 4 * 8;
                            return new s.init(n, t)
                        }
                    }
                        , p = u.Utf8 = {
                        stringify: function (e) {
                            try {
                                return decodeURIComponent(escape(d.stringify(e)))
                            } catch (RE) {
                                throw new Error("Malformed UTF-8 data")
                            }
                        },
                        parse: function (e) {
                            return d.parse(unescape(encodeURIComponent(e)))
                        }
                    }
                        , f = i.BufferedBlockAlgorithm = l.extend({
                        reset: function () {
                            this._data = new s.init,
                                this._nDataBytes = 0
                        },
                        _append: function (e) {
                            "string" == typeof e && (e = p.parse(e)),
                                this._data.concat(e),
                                this._nDataBytes += e.sigBytes
                        },
                        _process: function (t) {
                            var n, r = this._data, o = r.words, a = r.sigBytes, i = this.blockSize, l = a / (4 * i),
                                u = (l = t ? e.ceil(l) : e.max((0 | l) - this._minBufferSize, 0)) * i, c = e.min(4 * u, a);
                            if (u) {
                                for (var d = 0; d < u; d += i)
                                    this._doProcessBlock(o, d);
                                n = o.splice(0, u),
                                    r.sigBytes -= c
                            }
                            return new s.init(n, c)
                        },
                        clone: function () {
                            var e = l.clone.call(this);
                            return e._data = this._data.clone(),
                                e
                        },
                        _minBufferSize: 0
                    });
                    i.Hasher = f.extend({
                        cfg: l.extend(),
                        init: function (e) {
                            this.cfg = this.cfg.extend(e),
                                this.reset()
                        },
                        reset: function () {
                            f.reset.call(this),
                                this._doReset()
                        },
                        update: function (e) {
                            return this._append(e),
                                this._process(),
                                this
                        },
                        finalize: function (e) {
                            return e && this._append(e),
                                this._doFinalize()
                        },
                        blockSize: 16,
                        _createHelper: function (e) {
                            return function (t, n) {
                                return new e.init(n).finalize(t)
                            }
                        },
                        _createHmacHelper: function (e) {
                            return function (t, n) {
                                return new h.HMAC.init(e, n).finalize(t)
                            }
                        }
                    });
                    var h = a.algo = {};
                    return a
                }(Math), XO);
                        
                var QO = {
                    exports: {}
                };
                QO.exports = function (e) {
                    return r = (n = e).lib,
                        o = r.Base,
                        a = r.WordArray,
                        (i = n.x64 = {}).Word = o.extend({
                            init: function (e, t) {
                                this.high = e,
                                    this.low = t
                            }
                        }),
                        i.WordArray = o.extend({
                            init: function (e, n) {
                                e = this.words = e || [],
                                    this.sigBytes = n != t ? n : 8 * e.length
                            },
                            toX32: function () {
                                for (var e = this.words, t = e.length, n = [], r = 0; r < t; r++) {
                                    var o = e[r];
                                    n.push(o.high),
                                        n.push(o.low)
                                }
                                return a.create(n, this.sigBytes)
                            },
                            clone: function () {
                                for (var e = o.clone.call(this), t = e.words = this.words.slice(0), n = t.length, r = 0; r < n; r++)
                                    t[r] = t[r].clone();
                                return e
                            }
                        }),
                        e;
                    var t, n, r, o, a, i
                }(JO.exports);
                        
                var nM = {
                    exports: {}
                };
                nM.exports = function (e) {
                    return function () {
                        var t = e
                            , n = t.lib.WordArray;
                        
                        function r(e, t, r) {
                            for (var o = [], a = 0, i = 0; i < t; i++)
                                if (i % 4) {
                                    var l = r[e.charCodeAt(i - 1)] << i % 4 * 2 | r[e.charCodeAt(i)] >>> 6 - i % 4 * 2;
                                    o[a >>> 2] |= l << 24 - a % 4 * 8,
                                        a++
                                }
                            return n.create(o, a)
                        }
                        
                        t.enc.Base64 = {
                            stringify: function (e) {
                                var t = e.words
                                    , n = e.sigBytes
                                    , r = this._map;
                                e.clamp();
                                for (var o = [], a = 0; a < n; a += 3)
                                    for (var i = (t[a >>> 2] >>> 24 - a % 4 * 8 & 255) << 16 | (t[a + 1 >>> 2] >>> 24 - (a + 1) % 4 * 8 & 255) << 8 | t[a + 2 >>> 2] >>> 24 - (a + 2) % 4 * 8 & 255, l = 0; l < 4 && a + .75 * l < n; l++)
                                        o.push(r.charAt(i >>> 6 * (3 - l) & 63));
                                var s = r.charAt(64);
                                if (s)
                                    for (; o.length % 4;)
                                        o.push(s);
                                return o.join("")
                            },
                            parse: function (e) {
                                var t = e.length
                                    , n = this._map
                                    , o = this._reverseMap;
                                if (!o) {
                                    o = this._reverseMap = [];
                                    for (var a = 0; a < n.length; a++)
                                        o[n.charCodeAt(a)] = a
                                }
                                var i = n.charAt(64);
                                if (i) {
                                    var l = e.indexOf(i);
                                    -1 !== l && (t = l)
                                }
                                return r(e, t, o)
                            },
                            _map: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
                        }
                    }(),
                        e.enc.Base64
                }(JO.exports);
                        
                var oM = {
                    exports: {}
                };
                oM.exports = function (e) {
                    return function (t) {
                        var n = e
                            , r = n.lib
                            , o = r.WordArray
                            , a = r.Hasher
                            , i = n.algo
                            , l = [];
                        !function () {
                            for (var e = 0; e < 64; e++)
                                l[e] = 4294967296 * t.abs(t.sin(e + 1)) | 0
                        }();
                        var s = i.MD5 = a.extend({
                            _doReset: function () {
                                this._hash = new o.init([1732584193, 4023233417, 2562383102, 271733878])
                            },
                            _doProcessBlock: function (e, t) {
                                for (var n = 0; n < 16; n++) {
                                    var r = t + n
                                        , o = e[r];
                                    e[r] = 16711935 & (o << 8 | o >>> 24) | 4278255360 & (o << 24 | o >>> 8)
                                }
                                var a = this._hash.words
                                    , i = e[t + 0]
                                    , s = e[t + 1]
                                    , f = e[t + 2]
                                    , h = e[t + 3]
                                    , g = e[t + 4]
                                    , v = e[t + 5]
                                    , m = e[t + 6]
                                    , y = e[t + 7]
                                    , b = e[t + 8]
                                    , C = e[t + 9]
                                    , _ = e[t + 10]
                                    , E = e[t + 11]
                                    , F = e[t + 12]
                                    , A = e[t + 13]
                                    , w = e[t + 14]
                                    , D = e[t + 15]
                                    , x = a[0]
                                    , S = a[1]
                                    , B = a[2]
                                    , k = a[3];
                                x = u(x, S, B, k, i, 7, l[0]),
                                    k = u(k, x, S, B, s, 12, l[1]),
                                    B = u(B, k, x, S, f, 17, l[2]),
                                    S = u(S, B, k, x, h, 22, l[3]),
                                    x = u(x, S, B, k, g, 7, l[4]),
                                    k = u(k, x, S, B, v, 12, l[5]),
                                    B = u(B, k, x, S, m, 17, l[6]),
                                    S = u(S, B, k, x, y, 22, l[7]),
                                    x = u(x, S, B, k, b, 7, l[8]),
                                    k = u(k, x, S, B, C, 12, l[9]),
                                    B = u(B, k, x, S, _, 17, l[10]),
                                    S = u(S, B, k, x, E, 22, l[11]),
                                    x = u(x, S, B, k, F, 7, l[12]),
                                    k = u(k, x, S, B, A, 12, l[13]),
                                    B = u(B, k, x, S, w, 17, l[14]),
                                    x = c(x, S = u(S, B, k, x, D, 22, l[15]), B, k, s, 5, l[16]),
                                    k = c(k, x, S, B, m, 9, l[17]),
                                    B = c(B, k, x, S, E, 14, l[18]),
                                    S = c(S, B, k, x, i, 20, l[19]),
                                    x = c(x, S, B, k, v, 5, l[20]),
                                    k = c(k, x, S, B, _, 9, l[21]),
                                    B = c(B, k, x, S, D, 14, l[22]),
                                    S = c(S, B, k, x, g, 20, l[23]),
                                    x = c(x, S, B, k, C, 5, l[24]),
                                    k = c(k, x, S, B, w, 9, l[25]),
                                    B = c(B, k, x, S, h, 14, l[26]),
                                    S = c(S, B, k, x, b, 20, l[27]),
                                    x = c(x, S, B, k, A, 5, l[28]),
                                    k = c(k, x, S, B, f, 9, l[29]),
                                    B = c(B, k, x, S, y, 14, l[30]),
                                    x = d(x, S = c(S, B, k, x, F, 20, l[31]), B, k, v, 4, l[32]),
                                    k = d(k, x, S, B, b, 11, l[33]),
                                    B = d(B, k, x, S, E, 16, l[34]),
                                    S = d(S, B, k, x, w, 23, l[35]),
                                    x = d(x, S, B, k, s, 4, l[36]),
                                    k = d(k, x, S, B, g, 11, l[37]),
                                    B = d(B, k, x, S, y, 16, l[38]),
                                    S = d(S, B, k, x, _, 23, l[39]),
                                    x = d(x, S, B, k, A, 4, l[40]),
                                    k = d(k, x, S, B, i, 11, l[41]),
                                    B = d(B, k, x, S, h, 16, l[42]),
                                    S = d(S, B, k, x, m, 23, l[43]),
                                    x = d(x, S, B, k, C, 4, l[44]),
                                    k = d(k, x, S, B, F, 11, l[45]),
                                    B = d(B, k, x, S, D, 16, l[46]),
                                    x = p(x, S = d(S, B, k, x, f, 23, l[47]), B, k, i, 6, l[48]),
                                    k = p(k, x, S, B, y, 10, l[49]),
                                    B = p(B, k, x, S, w, 15, l[50]),
                                    S = p(S, B, k, x, v, 21, l[51]),
                                    x = p(x, S, B, k, F, 6, l[52]),
                                    k = p(k, x, S, B, h, 10, l[53]),
                                    B = p(B, k, x, S, _, 15, l[54]),
                                    S = p(S, B, k, x, s, 21, l[55]),
                                    x = p(x, S, B, k, b, 6, l[56]),
                                    k = p(k, x, S, B, D, 10, l[57]),
                                    B = p(B, k, x, S, m, 15, l[58]),
                                    S = p(S, B, k, x, A, 21, l[59]),
                                    x = p(x, S, B, k, g, 6, l[60]),
                                    k = p(k, x, S, B, E, 10, l[61]),
                                    B = p(B, k, x, S, f, 15, l[62]),
                                    S = p(S, B, k, x, C, 21, l[63]),
                                    a[0] = a[0] + x | 0,
                                    a[1] = a[1] + S | 0,
                                    a[2] = a[2] + B | 0,
                                    a[3] = a[3] + k | 0
                            },
                            _doFinalize: function () {
                                var e = this._data
                                    , n = e.words
                                    , r = 8 * this._nDataBytes
                                    , o = 8 * e.sigBytes;
                                n[o >>> 5] |= 128 << 24 - o % 32;
                                var a = t.floor(r / 4294967296)
                                    , i = r;
                                n[15 + (o + 64 >>> 9 << 4)] = 16711935 & (a << 8 | a >>> 24) | 4278255360 & (a << 24 | a >>> 8),
                                    n[14 + (o + 64 >>> 9 << 4)] = 16711935 & (i << 8 | i >>> 24) | 4278255360 & (i << 24 | i >>> 8),
                                    e.sigBytes = 4 * (n.length + 1),
                                    this._process();
                                for (var l = this._hash, s = l.words, u = 0; u < 4; u++) {
                                    var c = s[u];
                                    s[u] = 16711935 & (c << 8 | c >>> 24) | 4278255360 & (c << 24 | c >>> 8)
                                }
                                return l
                            },
                            clone: function () {
                                var e = a.clone.call(this);
                                return e._hash = this._hash.clone(),
                                    e
                            }
                        });
                        
                        function u(e, t, n, r, o, a, i) {
                            var l = e + (t & n | ~t & r) + o + i;
                            return (l << a | l >>> 32 - a) + t
                        }
                        
                        function c(e, t, n, r, o, a, i) {
                            var l = e + (t & r | n & ~r) + o + i;
                            return (l << a | l >>> 32 - a) + t
                        }
                        
                        function d(e, t, n, r, o, a, i) {
                            var l = e + (t ^ n ^ r) + o + i;
                            return (l << a | l >>> 32 - a) + t
                        }
                        
                        function p(e, t, n, r, o, a, i) {
                            var l = e + (n ^ (t | ~r)) + o + i;
                            return (l << a | l >>> 32 - a) + t
                        }
                        
                        n.MD5 = a._createHelper(s),
                            n.HmacMD5 = a._createHmacHelper(s)
                    }(Math),
                        e.MD5
                }(JO.exports);
                        
                var hM = {
                    exports: {}
                };
                hM.exports = function (e) {
                    return n = (t = e).lib,
                        r = n.Base,
                        o = n.WordArray,
                        a = t.algo,
                        i = a.MD5,
                        l = a.EvpKDF = r.extend({
                            cfg: r.extend({
                                keySize: 4,
                                hasher: i,
                                iterations: 1
                            }),
                            init: function (e) {
                                this.cfg = this.cfg.extend(e)
                            },
                            compute: function (e, t) {
                                for (var n, r = this.cfg, a = r.hasher.create(), i = o.create(), l = i.words, s = r.keySize, u = r.iterations; l.length < s;) {
                                    n && a.update(n),
                                        n = a.update(e).finalize(t),
                                        a.reset();
                                    for (var c = 1; c < u; c++)
                                        n = a.finalize(n),
                                            a.reset();
                                    i.concat(n)
                                }
                                return i.sigBytes = 4 * s,
                                    i
                            }
                        }),
                        t.EvpKDF = function (e, t, n) {
                            return l.create(n).compute(e, t)
                        }
                        ,
                        e.EvpKDF;
                    var t, n, r, o, a, i, l
                }(JO.exports);
                        
                var gM = {
                    exports: {}
                };
                gM.exports = function (e) {
                    e.lib.Cipher || function (t) {
                        var n = e
                            , r = n.lib
                            , o = r.Base
                            , a = r.WordArray
                            , i = r.BufferedBlockAlgorithm
                            , l = n.enc;
                        l.Utf8;
                        var s = l.Base64
                            , u = n.algo.EvpKDF
                            , c = r.Cipher = i.extend({
                            cfg: o.extend(),
                            createEncryptor: function (e, t) {
                                return this.create(this._ENC_XFORM_MODE, e, t)
                            },
                            createDecryptor: function (e, t) {
                                return this.create(this._DEC_XFORM_MODE, e, t)
                            },
                            init: function (e, t, n) {
                                this.cfg = this.cfg.extend(n),
                                    this._xformMode = e,
                                    this._key = t,
                                    this.reset()
                            },
                            reset: function () {
                                i.reset.call(this),
                                    this._doReset()
                            },
                            process: function (e) {
                                return this._append(e),
                                    this._process()
                            },
                            finalize: function (e) {
                                return e && this._append(e),
                                    this._doFinalize()
                            },
                            keySize: 4,
                            ivSize: 4,
                            _ENC_XFORM_MODE: 1,
                            _DEC_XFORM_MODE: 2,
                            _createHelper: function () {
                                function e(e) {
                                    return "string" == typeof e ? b : m
                                }
                        
                                return function (t) {
                                    return {
                                        encrypt: function (n, r, o) {
                                            return e(r).encrypt(t, n, r, o)
                                        },
                                        decrypt: function (n, r, o) {
                                            return e(r).decrypt(t, n, r, o)
                                        }
                                    }
                                }
                            }()
                        });
                        
                        r.StreamCipher = c.extend({
                            _doFinalize: function () {
                                return this._process(!0)
                            },
                            blockSize: 1
                        });
                        var d = n.mode = {}
                            , p = r.BlockCipherMode = o.extend({
                            createEncryptor: function (e, t) {
                                return this.Encryptor.create(e, t)
                            },
                            createDecryptor: function (e, t) {
                                return this.Decryptor.create(e, t)
                            },
                            init: function (e, t) {
                                this._cipher = e,
                                    this._iv = t
                            }
                        })
                            , f = d.CBC = function () {
                            var e = p.extend();
                        
                            function n(e, n, r) {
                                var o, a = this._iv;
                                a ? (o = a,
                                    this._iv = t) : o = this._prevBlock;
                                for (var i = 0; i < r; i++)
                                    e[n + i] ^= o[i]
                            }
                        
                            return e.Encryptor = e.extend({
                                processBlock: function (e, t) {
                                    var r = this._cipher
                                        , o = r.blockSize;
                                    n.call(this, e, t, o),
                                        r.encryptBlock(e, t),
                                        this._prevBlock = e.slice(t, t + o)
                                }
                            }),
                                e.Decryptor = e.extend({
                                    processBlock: function (e, t) {
                                        var r = this._cipher
                                            , o = r.blockSize
                                            , a = e.slice(t, t + o);
                                        r.decryptBlock(e, t),
                                            n.call(this, e, t, o),
                                            this._prevBlock = a
                                    }
                                }),
                                e
                        }()
                            , h = (n.pad = {}).Pkcs7 = {
                            pad: function (e, t) {
                                for (var n = 4 * t, r = n - e.sigBytes % n, o = r << 24 | r << 16 | r << 8 | r, i = [], l = 0; l < r; l += 4)
                                    i.push(o);
                                var s = a.create(i, r);
                                e.concat(s)
                            },
                            unpad: function (e) {
                                var t = 255 & e.words[e.sigBytes - 1 >>> 2];
                                e.sigBytes -= t
                            }
                        };
                        r.BlockCipher = c.extend({
                            cfg: c.cfg.extend({
                                mode: f,
                                padding: h
                            }),
                            reset: function () {
                                var e;
                                c.reset.call(this);
                                var t = this.cfg
                                    , n = t.iv
                                    , r = t.mode;
                                this._xformMode == this._ENC_XFORM_MODE ? e = r.createEncryptor : (e = r.createDecryptor,
                                    this._minBufferSize = 1),
                                    this._mode && this._mode.__creator == e ? this._mode.init(this, n && n.words) : (this._mode = e.call(r, this, n && n.words),
                                        this._mode.__creator = e)
                            },
                            _doProcessBlock: function (e, t) {
                                this._mode.processBlock(e, t)
                            },
                            _doFinalize: function () {
                                var e, t = this.cfg.padding;
                                return this._xformMode == this._ENC_XFORM_MODE ? (t.pad(this._data, this.blockSize),
                                    e = this._process(!0)) : (e = this._process(!0),
                                    t.unpad(e)),
                                    e
                            },
                            blockSize: 4
                        });
                        var g = r.CipherParams = o.extend({
                            init: function (e) {
                                this.mixIn(e)
                            },
                            toString: function (e) {
                                return (e || this.formatter).stringify(this)
                            }
                        })
                            , v = (n.format = {}).OpenSSL = {
                            stringify: function (e) {
                                var t = e.ciphertext
                                    , n = e.salt;
                                return (n ? a.create([1398893684, 1701076831]).concat(n).concat(t) : t).toString(s)
                            },
                            parse: function (e) {
                                var t, n = s.parse(e), r = n.words;
                                return 1398893684 == r[0] && 1701076831 == r[1] && (t = a.create(r.slice(2, 4)),
                                    r.splice(0, 4),
                                    n.sigBytes -= 16),
                                    g.create({
                                        ciphertext: n,
                                        salt: t
                                    })
                            }
                        }
                            , m = r.SerializableCipher = o.extend({
                            cfg: o.extend({
                                format: v
                            }),
                            encrypt: function (e, t, n, r) {
                                r = this.cfg.extend(r);
                                var o = e.createEncryptor(n, r)
                                    , a = o.finalize(t)
                                    , i = o.cfg;
                                return g.create({
                                    ciphertext: a,
                                    key: n,
                                    iv: i.iv,
                                    algorithm: e,
                                    mode: i.mode,
                                    padding: i.padding,
                                    blockSize: e.blockSize,
                                    formatter: r.format
                                })
                            },
                            decrypt: function (e, t, n, r) {
                                return r = this.cfg.extend(r),
                                    t = this._parse(t, r.format),
                                    e.createDecryptor(n, r).finalize(t.ciphertext)
                            },
                            _parse: function (e, t) {
                                return "string" == typeof e ? t.parse(e, this) : e
                            }
                        })
                            , y = (n.kdf = {}).OpenSSL = {
                            execute: function (e, t, n, r) {
                                r || (r = a.random(8));
                                var o = u.create({
                                    keySize: t + n
                                }).compute(e, r)
                                    , i = a.create(o.words.slice(t), 4 * n);
                                return o.sigBytes = 4 * t,
                                    g.create({
                                        key: o,
                                        iv: i,
                                        salt: r
                                    })
                            }
                        }
                            , b = r.PasswordBasedCipher = m.extend({
                            cfg: m.cfg.extend({
                                kdf: y
                            }),
                            encrypt: function (e, t, n, r) {
                                var o = (r = this.cfg.extend(r)).kdf.execute(n, e.keySize, e.ivSize);
                                r.iv = o.iv;
                                var a = m.encrypt.call(this, e, t, o.key, r);
                                return a.mixIn(o),
                                    a
                            },
                            decrypt: function (e, t, n, r) {
                                r = this.cfg.extend(r),
                                    t = this._parse(t, r.format);
                                var o = r.kdf.execute(n, e.keySize, e.ivSize, t.salt);
                                return r.iv = o.iv,
                                    m.decrypt.call(this, e, t, o.key, r)
                            }
                        })
                    }()
                }(JO.exports);
                        
                var xM = {
                    exports: {}
                };
                xM.exports = function (e) {
                    return function () {
                        var t = e
                            , n = t.lib.BlockCipher
                            , r = t.algo
                            , o = []
                            , a = []
                            , i = []
                            , l = []
                            , s = []
                            , u = []
                            , c = []
                            , d = []
                            , p = []
                            , f = [];
                        !function () {
                            for (var e = [], t = 0; t < 256; t++)
                                e[t] = t < 128 ? t << 1 : t << 1 ^ 283;
                            var n = 0
                                , r = 0;
                            for (t = 0; t < 256; t++) {
                                var h = r ^ r << 1 ^ r << 2 ^ r << 3 ^ r << 4;
                                h = h >>> 8 ^ 255 & h ^ 99,
                                    o[n] = h,
                                    a[h] = n;
                                var g = e[n]
                                    , v = e[g]
                                    , m = e[v]
                                    , y = 257 * e[h] ^ 16843008 * h;
                                i[n] = y << 24 | y >>> 8,
                                    l[n] = y << 16 | y >>> 16,
                                    s[n] = y << 8 | y >>> 24,
                                    u[n] = y,
                                    y = 16843009 * m ^ 65537 * v ^ 257 * g ^ 16843008 * n,
                                    c[h] = y << 24 | y >>> 8,
                                    d[h] = y << 16 | y >>> 16,
                                    p[h] = y << 8 | y >>> 24,
                                    f[h] = y,
                                    n ? (n = g ^ e[e[e[m ^ g]]],
                                        r ^= e[e[r]]) : n = r = 1
                            }
                        }();
                        var h = [0, 1, 2, 4, 8, 16, 32, 64, 128, 27, 54]
                            , g = r.AES = n.extend({
                            _doReset: function () {
                                if (!this._nRounds || this._keyPriorReset !== this._key) {
                                    for (var e = this._keyPriorReset = this._key, t = e.words, n = e.sigBytes / 4, r = 4 * ((this._nRounds = n + 6) + 1), a = this._keySchedule = [], i = 0; i < r; i++)
                                        i < n ? a[i] = t[i] : (u = a[i - 1],
                                            i % n ? n > 6 && i % n == 4 && (u = o[u >>> 24] << 24 | o[u >>> 16 & 255] << 16 | o[u >>> 8 & 255] << 8 | o[255 & u]) : (u = o[(u = u << 8 | u >>> 24) >>> 24] << 24 | o[u >>> 16 & 255] << 16 | o[u >>> 8 & 255] << 8 | o[255 & u],
                                                u ^= h[i / n | 0] << 24),
                                            a[i] = a[i - n] ^ u);
                                    for (var l = this._invKeySchedule = [], s = 0; s < r; s++) {
                                        if (i = r - s,
                                        s % 4)
                                            var u = a[i];
                                        else
                                            u = a[i - 4];
                                        l[s] = s < 4 || i <= 4 ? u : c[o[u >>> 24]] ^ d[o[u >>> 16 & 255]] ^ p[o[u >>> 8 & 255]] ^ f[o[255 & u]]
                                    }
                                }
                            },
                            encryptBlock: function (e, t) {
                                this._doCryptBlock(e, t, this._keySchedule, i, l, s, u, o)
                            },
                            decryptBlock: function (e, t) {
                                var n = e[t + 1];
                                e[t + 1] = e[t + 3],
                                    e[t + 3] = n,
                                    this._doCryptBlock(e, t, this._invKeySchedule, c, d, p, f, a),
                                    n = e[t + 1],
                                    e[t + 1] = e[t + 3],
                                    e[t + 3] = n
                            },
                            _doCryptBlock: function (e, t, n, r, o, a, i, l) {
                                for (var s = this._nRounds, u = e[t] ^ n[0], c = e[t + 1] ^ n[1], d = e[t + 2] ^ n[2], p = e[t + 3] ^ n[3], f = 4, h = 1; h < s; h++) {
                                    var g = r[u >>> 24] ^ o[c >>> 16 & 255] ^ a[d >>> 8 & 255] ^ i[255 & p] ^ n[f++]
                                        , v = r[c >>> 24] ^ o[d >>> 16 & 255] ^ a[p >>> 8 & 255] ^ i[255 & u] ^ n[f++]
                                        , m = r[d >>> 24] ^ o[p >>> 16 & 255] ^ a[u >>> 8 & 255] ^ i[255 & c] ^ n[f++]
                                        , y = r[p >>> 24] ^ o[u >>> 16 & 255] ^ a[c >>> 8 & 255] ^ i[255 & d] ^ n[f++];
                                    u = g,
                                        c = v,
                                        d = m,
                                        p = y
                                }
                                g = (l[u >>> 24] << 24 | l[c >>> 16 & 255] << 16 | l[d >>> 8 & 255] << 8 | l[255 & p]) ^ n[f++],
                                    v = (l[c >>> 24] << 24 | l[d >>> 16 & 255] << 16 | l[p >>> 8 & 255] << 8 | l[255 & u]) ^ n[f++],
                                    m = (l[d >>> 24] << 24 | l[p >>> 16 & 255] << 16 | l[u >>> 8 & 255] << 8 | l[255 & c]) ^ n[f++],
                                    y = (l[p >>> 24] << 24 | l[u >>> 16 & 255] << 16 | l[c >>> 8 & 255] << 8 | l[255 & d]) ^ n[f++],
                                    e[t] = g,
                                    e[t + 1] = v,
                                    e[t + 2] = m,
                                    e[t + 3] = y
                            },
                            keySize: 8
                        });
                        t.AES = n._createHelper(g)
                    }(),
                        e.AES
                }(JO.exports);
                        
                        
                var OM = qO.exports = JO.exports;
                
                function enc(data, key) {
                    return OM.AES.encrypt(data, key).toString()
                }
                
            """;
}
