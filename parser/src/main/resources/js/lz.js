/**
 * 蓝奏云解析器 JS 沙箱：伪装 jQuery / document / window。
 * 新版页面会用 document.cookie、location.reload、querySelector、
 * $('#pwd').val()、.html()、.css() 等，这里做成可链式的最小实现。
 * kdns.js 在浏览器里是 `var killdns = true`，需一并注入，否则 kd 会被改成 0。
 */

var signObj;
var __lzPwd = '';
var killdns = true;

function __lzSetPwd(p) {
  __lzPwd = p == null ? '' : String(p);
}

function __lzEl(id) {
  var nid = String(id == null ? '' : id).replace(/^[#.]/, '');
  var el = {
    id: nid,
    _value: nid === 'pwd' ? __lzPwd : '',
    checked: false,
    disabled: false,
    innerHTML: '',
    innerText: '',
    textContent: '',
    className: '',
    style: { display: '', visibility: '', width: '', height: '' },
    classList: {
      add: function () {},
      remove: function () {},
      contains: function () { return false; },
      toggle: function () {}
    },
    setAttribute: function () {},
    getAttribute: function () { return null; },
    removeAttribute: function () {},
    addEventListener: function (t, fn) {
      if (typeof fn === 'function') {
        try { fn(); } catch (e) {}
      }
    },
    removeEventListener: function () {},
    appendChild: function (n) { return n; },
    removeChild: function (n) { return n; },
    insertBefore: function (n) { return n; },
    click: function () {},
    focus: function () {},
    blur: function () {},
    submit: function () {},
    select: function () {},
    reset: function () {}
  };
  el.parentNode = el;
  el.parentElement = el;
  el.children = [];
  el.childNodes = [];
  el.firstChild = null;
  el.lastChild = null;
  try {
    Object.defineProperty(el, 'value', {
      get: function () { return nid === 'pwd' ? __lzPwd : el._value; },
      set: function (v) {
        el._value = v;
        if (nid === 'pwd') {
          __lzPwd = v == null ? '' : String(v);
        }
      }
    });
  } catch (e) {
    el.value = el._value;
  }
  return el;
}

function __lzJq(sel) {
  var id = '';
  if (typeof sel === 'string') {
    id = sel.replace(/^[#.]/, '');
  } else if (sel && sel.id) {
    id = String(sel.id);
  }
  var el = (sel && sel.style && sel.addEventListener) ? sel : __lzEl(id);
  var api = {
    0: el,
    length: 1,
    selector: sel,
    ready: function (fn) {
      if (typeof fn === 'function') {
        try { fn(jQuery); } catch (e) {}
      }
      return api;
    },
    on: function (t, fn) {
      if (typeof fn === 'function') {
        try { fn(); } catch (e) {}
      }
      return api;
    },
    off: function () { return api; },
    bind: function (t, fn) { return api.on(t, fn); },
    unbind: function () { return api; },
    click: function (fn) {
      if (typeof fn === 'function') {
        try { fn(); } catch (e) {}
      }
      return api;
    },
    focus: function (fn) {
      if (typeof fn === 'function') {
        try { fn(); } catch (e) {}
      }
      return api;
    },
    blur: function () { return api; },
    keyup: function (fn) {
      if (typeof fn === 'function') {
        try { fn(); } catch (e) {}
      }
      return api;
    },
    keydown: function (fn) { return api.keyup(fn); },
    keypress: function (fn) { return api.keyup(fn); },
    submit: function (fn) { return api.click(fn); },
    change: function (fn) { return api.click(fn); },
    hover: function () { return api; },
    val: function (v) {
      if (arguments.length === 0) {
        if (typeof el.value !== 'undefined') {
          return el.value;
        }
        return (id === 'pwd') ? __lzPwd : '';
      }
      el.value = v;
      return api;
    },
    html: function (v) {
      if (arguments.length === 0) {
        return el.innerHTML;
      }
      el.innerHTML = v;
      return api;
    },
    text: function (v) {
      if (arguments.length === 0) {
        return el.innerText;
      }
      el.innerText = v;
      el.textContent = v;
      return api;
    },
    attr: function (k, v) {
      if (arguments.length < 2) {
        return null;
      }
      return api;
    },
    prop: function (k, v) {
      if (arguments.length < 2) {
        return false;
      }
      return api;
    },
    css: function () { return api; },
    addClass: function () { return api; },
    removeClass: function () { return api; },
    toggleClass: function () { return api; },
    hasClass: function () { return false; },
    show: function () {
      el.style.display = '';
      return api;
    },
    hide: function () {
      el.style.display = 'none';
      return api;
    },
    fadeIn: function () { return api; },
    fadeOut: function () { return api; },
    animate: function () { return api; },
    find: function () { return api; },
    parent: function () { return api; },
    children: function () { return api; },
    eq: function () { return api; },
    first: function () { return api; },
    last: function () { return api; },
    each: function (fn) {
      if (typeof fn === 'function') {
        try { fn.call(el, 0, el); } catch (e) {}
      }
      return api;
    },
    append: function () { return api; },
    prepend: function () { return api; },
    remove: function () { return api; },
    empty: function () { return api; },
    ajax: function (obj) {
      signObj = obj;
      return api;
    },
    get: function () { return el; }
  };
  return api;
}

var $, jQuery;
$ = jQuery = function (sel) {
  if (typeof sel === 'function') {
    try { sel(jQuery); } catch (e) {}
    return __lzJq(document);
  }
  return __lzJq(sel);
};

jQuery.fn = jQuery.prototype = {
  init: function (sel) {
    return __lzJq(sel);
  }
};
jQuery.fn.init.prototype = jQuery.fn;
$.fn = jQuery.fn;

$.ajax = function (obj) {
  signObj = obj;
  return {
    done: function () { return this; },
    fail: function () { return this; },
    always: function () { return this; }
  };
};
$.get = function () {};
$.post = function () {};
$.extend = function () {
  var t = arguments[0] || {};
  for (var i = 1; i < arguments.length; i++) {
    var s = arguments[i];
    if (s) {
      for (var k in s) {
        if (s.hasOwnProperty(k)) {
          t[k] = s[k];
        }
      }
    }
  }
  return t;
};
$.each = function (obj, fn) {
  if (!obj || typeof fn !== 'function') {
    return obj;
  }
  if (typeof obj.length === 'number') {
    for (var i = 0; i < obj.length; i++) {
      fn.call(obj[i], i, obj[i]);
    }
  } else {
    for (var k in obj) {
      if (obj.hasOwnProperty(k)) {
        fn.call(obj[k], k, obj[k]);
      }
    }
  }
  return obj;
};
$.isFunction = function (f) { return typeof f === 'function'; };
$.isArray = function (a) {
  return Object.prototype.toString.call(a) === '[object Array]';
};
$.trim = function (s) {
  return s == null ? '' : String(s).replace(/^\s+|\s+$/g, '');
};

var __lzLocation = {
  href: '',
  search: '',
  pathname: '/',
  hash: '',
  host: '',
  hostname: '',
  protocol: 'https:',
  port: '',
  origin: '',
  assign: function () {},
  replace: function () {},
  reload: function () {}
};

var document = {
  cookie: '',
  title: '',
  domain: '',
  referrer: '',
  readyState: 'complete',
  hidden: false,
  visibilityState: 'visible',
  documentElement: null,
  body: null,
  head: null,
  location: __lzLocation,
  getElementById: function (id) { return __lzEl(id); },
  getElementsByClassName: function () { return []; },
  getElementsByTagName: function (t) {
    return t === 'script' ? [] : [__lzEl(t)];
  },
  getElementsByName: function () { return []; },
  querySelector: function (s) { return __lzEl(s); },
  querySelectorAll: function (s) { return [__lzEl(s)]; },
  createElement: function (t) { return __lzEl(t); },
  createTextNode: function (t) { return { nodeValue: t, data: t }; },
  createDocumentFragment: function () { return __lzEl('fragment'); },
  addEventListener: function (t, fn) {
    if (typeof fn === 'function') {
      try { fn(); } catch (e) {}
    }
  },
  removeEventListener: function () {},
  write: function () {},
  writeln: function () {},
  open: function () {},
  close: function () {}
};
document.documentElement = __lzEl('html');
document.body = __lzEl('body');
document.head = __lzEl('head');

var navigator = {
  userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36',
  platform: 'Win32',
  language: 'zh-CN',
  cookieEnabled: true,
  onLine: true
};

var location = __lzLocation;
var console = {
  log: function () {},
  warn: function () {},
  error: function () {},
  info: function () {},
  debug: function () {}
};

function setTimeout(fn, delay) {
  if (typeof fn === 'function' && (!delay || delay <= 0)) {
    try { fn(); } catch (e) {}
  }
  return 0;
}
function setInterval() { return 0; }
function clearTimeout() {}
function clearInterval() {}

var window = {
  location: __lzLocation,
  document: document,
  navigator: navigator,
  console: console,
  innerWidth: 1920,
  innerHeight: 1080,
  setTimeout: setTimeout,
  setInterval: setInterval,
  clearTimeout: clearTimeout,
  clearInterval: clearInterval,
  addEventListener: function (t, fn) {
    if (typeof fn === 'function') {
      try { fn(); } catch (e) {}
    }
  },
  removeEventListener: function () {},
  atob: function (s) { return s; },
  btoa: function (s) { return s; }
};
window.window = window;
window.top = window;
window.self = window;
window.parent = window;
window.jQuery = jQuery;
window.$ = $;

var top = window;
var self = window;
var parent = window;
