// ==FetchRuntime==
// @name         Fetch API Polyfill for ES5
// @description  Fetch API and Promise implementation for ES5 JavaScript engines
// @version      1.0.0
// @author       QAIU
// ==============

/**
 * Simple Promise implementation compatible with ES5
 * Supports basic Promise functionality needed for fetch API
 */
function SimplePromise(executor) {
  var state = 'pending';
  var value;
  var handlers = [];
  var self = this;

  function resolve(result) {
    if (state !== 'pending') return;
    state = 'fulfilled';
    value = result;
    handlers.forEach(handle);
    handlers = [];
  }

  function reject(err) {
    if (state !== 'pending') return;
    state = 'rejected';
    value = err;
    handlers.forEach(handle);
    handlers = [];
  }

  function handle(handler) {
    if (state === 'pending') {
      handlers.push(handler);
    } else {
      setTimeout(function() {
        if (state === 'fulfilled' && typeof handler.onFulfilled === 'function') {
          try {
            var result = handler.onFulfilled(value);
            if (result && typeof result.then === 'function') {
              result.then(handler.resolve, handler.reject);
            } else {
              handler.resolve(result);
            }
          } catch (e) {
            handler.reject(e);
          }
        }
        if (state === 'rejected' && typeof handler.onRejected === 'function') {
          try {
            var result = handler.onRejected(value);
            if (result && typeof result.then === 'function') {
              result.then(handler.resolve, handler.reject);
            } else {
              handler.resolve(result);
            }
          } catch (e) {
            handler.reject(e);
          }
        } else if (state === 'rejected' && !handler.onRejected) {
          handler.reject(value);
        }
      }, 0);
    }
  }

  this.then = function(onFulfilled, onRejected) {
    return new SimplePromise(function(resolveNext, rejectNext) {
      handle({
        onFulfilled: onFulfilled,
        onRejected: onRejected,
        resolve: resolveNext,
        reject: rejectNext
      });
    });
  };

  this['catch'] = function(onRejected) {
    return this.then(null, onRejected);
  };

  this['finally'] = function(onFinally) {
    return this.then(
      function(value) {
        return SimplePromise.resolve(onFinally()).then(function() {
          return value;
        });
      },
      function(reason) {
        return SimplePromise.resolve(onFinally()).then(function() {
          throw reason;
        });
      }
    );
  };

  try {
    executor(resolve, reject);
  } catch (e) {
    reject(e);
  }
}

// Static methods
SimplePromise.resolve = function(value) {
  if (value && typeof value.then === 'function') {
    return value;
  }
  return new SimplePromise(function(resolve) {
    resolve(value);
  });
};

SimplePromise.reject = function(reason) {
  return new SimplePromise(function(resolve, reject) {
    reject(reason);
  });
};

SimplePromise.all = function(promises) {
  return new SimplePromise(function(resolve, reject) {
    var results = [];
    var remaining = promises.length;
    
    if (remaining === 0) {
      resolve(results);
      return;
    }
    
    function handleResult(index, value) {
      results[index] = value;
      remaining--;
      if (remaining === 0) {
        resolve(results);
      }
    }
    
    for (var i = 0; i < promises.length; i++) {
      (function(index) {
        var promise = promises[index];
        if (promise && typeof promise.then === 'function') {
          promise.then(
            function(value) { handleResult(index, value); },
            reject
          );
        } else {
          handleResult(index, promise);
        }
      })(i);
    }
  });
};

SimplePromise.race = function(promises) {
  return new SimplePromise(function(resolve, reject) {
    for (var i = 0; i < promises.length; i++) {
      var promise = promises[i];
      if (promise && typeof promise.then === 'function') {
        promise.then(resolve, reject);
      } else {
        resolve(promise);
      }
    }
  });
};

// Make Promise global if not already defined
if (typeof Promise === 'undefined') {
  var Promise = SimplePromise;
}

/**
 * Response object that mimics the Fetch API Response
 */
function FetchResponse(jsHttpResponse) {
  this._jsResponse = jsHttpResponse;
  this.status = jsHttpResponse.statusCode();
  this.ok = this.status >= 200 && this.status < 300;
  this.statusText = this.ok ? 'OK' : 'Error';
  this.headers = {
    get: function(name) {
      return jsHttpResponse.header(name);
    },
    has: function(name) {
      return jsHttpResponse.header(name) !== null;
    },
    entries: function() {
      var headerMap = jsHttpResponse.headers();
      var entries = [];
      for (var key in headerMap) {
        if (headerMap.hasOwnProperty(key)) {
          entries.push([key, headerMap[key]]);
        }
      }
      return entries;
    }
  };
}

FetchResponse.prototype.text = function() {
  var body = this._jsResponse.body();
  return SimplePromise.resolve(body || '');
};

FetchResponse.prototype.json = function() {
  var self = this;
  return this.text().then(function(text) {
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error('Invalid JSON: ' + e.message);
    }
  });
};

FetchResponse.prototype.arrayBuffer = function() {
  var bytes = this._jsResponse.bodyBytes();
  return SimplePromise.resolve(bytes);
};

FetchResponse.prototype.blob = function() {
  // Blob not supported in ES5, return bytes
  return this.arrayBuffer();
};

/**
 * Fetch API implementation using JavaFetch bridge
 * @param {string} url - Request URL
 * @param {Object} options - Fetch options (method, headers, body, etc.)
 * @returns {Promise<FetchResponse>}
 */
function fetch(url, options) {
  return new SimplePromise(function(resolve, reject) {
    try {
      // Parse options
      options = options || {};
      var method = (options.method || 'GET').toUpperCase();
      var headers = options.headers || {};
      var body = options.body;
      
      // Prepare request options for JavaFetch
      var requestOptions = {
        method: method,
        headers: {}
      };
      
      // Convert headers to simple object
      if (headers) {
        if (typeof headers.forEach === 'function') {
          // Headers object
          headers.forEach(function(value, key) {
            requestOptions.headers[key] = value;
          });
        } else if (typeof headers === 'object') {
          // Plain object
          for (var key in headers) {
            if (headers.hasOwnProperty(key)) {
              requestOptions.headers[key] = headers[key];
            }
          }
        }
      }
      
      // Add body if present
      if (body !== undefined && body !== null) {
        if (typeof body === 'string') {
          requestOptions.body = body;
        } else if (typeof body === 'object') {
          // Assume JSON
          requestOptions.body = JSON.stringify(body);
          if (!requestOptions.headers['Content-Type'] && !requestOptions.headers['content-type']) {
            requestOptions.headers['Content-Type'] = 'application/json';
          }
        }
      }
      
      // Call JavaFetch bridge
      var jsHttpResponse = JavaFetch.fetch(url, requestOptions);
      
      // Create Response object
      var response = new FetchResponse(jsHttpResponse);
      resolve(response);
      
    } catch (e) {
      reject(e);
    }
  });
}

// Export for global use
if (typeof window !== 'undefined') {
  window.fetch = fetch;
  window.Promise = Promise;
} else if (typeof global !== 'undefined') {
  global.fetch = fetch;
  global.Promise = Promise;
}
