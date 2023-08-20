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
