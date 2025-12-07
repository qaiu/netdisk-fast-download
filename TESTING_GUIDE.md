# Playground Access Control - Testing Guide

## Quick Test Scenarios

### Scenario 1: Disabled Mode (Default)
**Configuration:**
```yaml
playground:
  enabled: false
  password: ""
```

**Expected Behavior:**
1. Navigate to `/playground`
2. Should see: "Playground未开启，请联系管理员在配置中启用此功能"
3. All API endpoints (`/v2/playground/*`) should return error

**API Test:**
```bash
curl http://localhost:6400/v2/playground/status
# Expected: {"code":200,"msg":"success","success":true,"data":{"enabled":false,"needPassword":false,"authed":false}}
```

---

### Scenario 2: Password-Protected Mode
**Configuration:**
```yaml
playground:
  enabled: true
  password: "test123"
```

**Expected Behavior:**
1. Navigate to `/playground`
2. Should see password input form with lock icon
3. Enter wrong password → Error message: "密码错误"
4. Enter correct password "test123" → Success, editor loads
5. Refresh page → Should remain authenticated

**API Tests:**
```bash
# Check status
curl http://localhost:6400/v2/playground/status
# Expected: {"enabled":true,"needPassword":true,"authed":false}

# Login with wrong password
curl -X POST http://localhost:6400/v2/playground/login \
  -H "Content-Type: application/json" \
  -d '{"password":"wrong"}'
# Expected: {"code":500,"msg":"密码错误","success":false}

# Login with correct password
curl -X POST http://localhost:6400/v2/playground/login \
  -H "Content-Type: application/json" \
  -d '{"password":"test123"}'
# Expected: {"code":200,"msg":"登录成功","success":true}

# Try to access without login (should fail)
curl http://localhost:6400/v2/playground/test \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"jsCode":"function parse(){return \"test\";}","shareUrl":"http://test.com"}'
# Expected: Error response
```

---

### Scenario 3: Public Access Mode
**Configuration:**
```yaml
playground:
  enabled: true
  password: ""
```

**Expected Behavior:**
1. Navigate to `/playground`
2. Should directly load the editor (no password prompt)
3. All features work immediately

**API Test:**
```bash
curl http://localhost:6400/v2/playground/status
# Expected: {"enabled":true,"needPassword":false,"authed":true}
```

⚠️ **Warning**: Only use this mode in localhost or secure internal network!

---

## Full Feature Tests

### 1. Status Endpoint
```bash
curl http://localhost:6400/v2/playground/status
```

Should return JSON with:
- `enabled`: boolean
- `needPassword`: boolean
- `authed`: boolean

### 2. Login Endpoint (when password is set)
```bash
curl -X POST http://localhost:6400/v2/playground/login \
  -H "Content-Type: application/json" \
  -d '{"password":"YOUR_PASSWORD"}'
```

### 3. Test Script Execution (after authentication)
```bash
curl -X POST http://localhost:6400/v2/playground/test \
  -H "Content-Type: application/json" \
  -d '{
    "jsCode": "function parse(shareLinkInfo, http, logger) { return \"http://example.com/file.zip\"; }",
    "shareUrl": "https://example.com/share/123",
    "pwd": "",
    "method": "parse"
  }'
```

### 4. Get Types Definition
```bash
curl http://localhost:6400/v2/playground/types.js
```

### 5. Parser Management (after authentication)
```bash
# List parsers
curl http://localhost:6400/v2/playground/parsers

# Get parser by ID
curl http://localhost:6400/v2/playground/parsers/1

# Delete parser
curl -X DELETE http://localhost:6400/v2/playground/parsers/1
```

---

## UI Testing Checklist

### When Disabled
- [ ] Page shows "Playground未开启" message
- [ ] No editor visible
- [ ] Clean, centered layout

### When Password Protected (Not Authenticated)
- [ ] Password input form visible
- [ ] Lock icon displayed
- [ ] Can toggle password visibility
- [ ] Enter key submits form
- [ ] Error message shows for wrong password
- [ ] Success message and editor loads on correct password

### When Password Protected (Authenticated)
- [ ] Editor loads immediately on page refresh
- [ ] All features work (run, save, format, etc.)
- [ ] Can execute tests
- [ ] Can save/load parsers

### When Public Access
- [ ] Editor loads immediately
- [ ] All features work without authentication
- [ ] No password prompt visible

---

## Configuration Examples

### Production (Recommended)
```yaml
playground:
  enabled: false
  password: ""
```

### Development Team (Public Network)
```yaml
playground:
  enabled: true
  password: "SecureP@ssw0rd2024!"
```

### Local Development
```yaml
playground:
  enabled: true
  password: ""
```

---

## Common Issues

### Issue: "Failed to extract session ID from cookie"
**Cause**: Cookie parsing error
**Solution**: This is logged as a warning and falls back to IP-based identification

### Issue: Editor doesn't load after correct password
**Cause**: Frontend state not updated
**Solution**: Check browser console for errors, ensure initPlayground() is called

### Issue: Authentication lost on page refresh
**Cause**: Server restarted (in-memory session storage)
**Solution**: Expected behavior - re-enter password after server restart

---

## Security Verification

### 1. Default Security
- [ ] Default config has `enabled: false`
- [ ] Cannot access playground without enabling
- [ ] No unintended API exposure

### 2. Password Protection
- [ ] Wrong password rejected
- [ ] Session persists across requests
- [ ] Different clients have independent sessions

### 3. API Protection
- [ ] All playground endpoints check authentication
- [ ] Status endpoint accessible without auth (returns state only)
- [ ] Login endpoint accessible without auth (for authentication)
- [ ] All other endpoints require authentication when password is set

---

## Performance Testing

### Load Test
```bash
# Test status endpoint
ab -n 1000 -c 10 http://localhost:6400/v2/playground/status
```

### Session Management Test
```bash
# Create multiple concurrent sessions
for i in {1..10}; do
  curl -X POST http://localhost:6400/v2/playground/login \
    -H "Content-Type: application/json" \
    -d '{"password":"test123"}' &
done
wait
```

---

## Cleanup

After testing, remember to:
1. Set `enabled: false` in production
2. Use strong passwords if enabling in public networks
3. Monitor access logs
4. Regularly review created parsers

---

## Documentation References

- Full documentation: `web-service/doc/PLAYGROUND_ACCESS_CONTROL.md`
- Main README: `README.md` (Playground Access Control section)
- Configuration file: `web-service/src/main/resources/app-dev.yml`

---

Last Updated: 2025-12-07
