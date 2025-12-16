# 🤖 AI Assistant Guide - API Simulator

## Overview

The AI Assistant is an intelligent helper that uses OpenAI's GPT models to generate API mappings from natural language descriptions. It analyzes your existing endpoints to maintain consistency and suggests appropriate configurations.

## ✨ Features

### 1. Natural Language Mapping Creation
- Describe what you want in plain English
- AI generates complete mapping configuration
- Maintains consistency with existing patterns
- One-click apply to create the endpoint

### 2. Context-Aware Suggestions
- Analyzes your workspace's existing mappings
- Learns naming conventions and patterns
- Suggests appropriate priorities and tags
- Matches response structure styles

### 3. Intelligent REST & GraphQL Support
- Generates both REST and GraphQL endpoints
- Proper HTTP methods and status codes
- Realistic response bodies with templating
- Appropriate headers and query parameters

---

## 🚀 Quick Start

### Step 1: Enable AI Assistant

Add to your `.env` file:
```bash
AI_ENABLED=true
OPENAI_API_KEY=your-openai-api-key-here
AI_MODEL=gpt-4o-mini
```

Or set environment variables:
```bash
export AI_ENABLED=true
export OPENAI_API_KEY=sk-proj-...
export AI_MODEL=gpt-4o-mini
```

### Step 2: Start the Application

```bash
cd backend
mvn spring-boot:run
```

### Step 3: Use the AI Assistant

1. Open the web UI: http://localhost:8080
2. Click the floating **"AI Assistant"** button (purple/blue gradient, bottom-right)
3. Type your request in natural language
4. Click **Apply Mapping** to create it

---

## 💬 Example Prompts

### REST Endpoints

```
Create a GET endpoint for fetching user by ID
```
**AI Generates:**
- Method: GET
- Path: /api/users/{id}
- Response: User object with id, name, email
- Status: 200

---

```
Add POST /api/orders with 200ms delay and 10% error rate
```
**AI Generates:**
- Method: POST
- Path: /api/orders
- Delay: 200ms fixed
- Error rate: 10% (returns 500)
- Response: Order created with ID

---

```
Create delete endpoint for removing products
```
**AI Generates:**
- Method: DELETE
- Path: /api/products/{id}
- Status: 204 (No Content)
- Follows REST conventions

---

### GraphQL Endpoints

```
Create a GraphQL endpoint for querying users
```
**AI Generates:**
- Endpoint Type: GRAPHQL
- Path: /graphql
- Operation: Query
- Response: GraphQL-formatted user data

---

### Complex Scenarios

```
Create a payment endpoint that accepts credit card info and returns transaction status with variable delay
```
**AI Generates:**
- Method: POST
- Path: /api/payments
- Body patterns: cardNumber, cvv, amount
- Variable delay: 100-800ms
- Response: Transaction object with status

---

## 🎯 Best Practices

### 1. Be Specific
**Good:** "Create POST endpoint for user registration with email validation"
**Better:** "Add POST /api/auth/register that accepts email and password, returns 201 with user ID"

### 2. Mention Key Details
- HTTP method (GET, POST, PUT, DELETE)
- Path structure (/api/resource/{id})
- Special requirements (delays, error rates, validations)
- Response format expectations

### 3. Reference Existing Patterns
**Example:** "Create an endpoint similar to the existing user endpoints but for products"

The AI will analyze your user endpoints and replicate the pattern.

### 4. Iterate if Needed
If the first result isn't perfect:
- Ask for specific changes: "Make the delay 500ms instead"
- Request additions: "Add a 15% error rate"
- Clarify requirements: "The response should include timestamps"

---

## 📊 How It Works

### Context Building

When you make a request, the AI Assistant:

1. **Extracts Keywords** from your prompt (e.g., "user", "delete", "endpoint")
2. **Searches Workspace** for relevant existing mappings
3. **Ranks by Relevance** based on keyword matches
4. **Sends Top 10** most relevant mappings as context to OpenAI
5. **Generates Mapping** following your patterns

### Example Context Flow

```
User Prompt: "Create delete user endpoint"
              ↓
Keywords: [delete, user]
              ↓
Search Workspace: 45 total mappings
              ↓
Relevant Mappings Found:
  ✓ GET /api/users/{id}    (has "user")
  ✓ POST /api/users        (has "user")
  ✓ DELETE /api/orders     (has "delete")
              ↓
Send to OpenAI with context
              ↓
AI Generates:
  DELETE /api/users/{id}
  Status: 204
  Priority: 5 (matches existing)
  Tags: [user, delete]
```

---

## ⚙️ Configuration Options

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_ENABLED` | `false` | Enable/disable AI Assistant |
| `OPENAI_API_KEY` | - | Your OpenAI API key (required) |
| `AI_MODEL` | `gpt-4o-mini` | OpenAI model to use |

### Application Properties (application.yml)

```yaml
simulator:
  ai:
    enabled: ${AI_ENABLED:false}
    provider: ${AI_PROVIDER:openai}
    api-key: ${OPENAI_API_KEY:}
    model: ${AI_MODEL:gpt-4o-mini}
    max-tokens: 2000
    temperature: 0.7
    max-context-mappings: 10
```

### Recommended Models

| Model | Speed | Quality | Cost | Best For |
|-------|-------|---------|------|----------|
| `gpt-4o-mini` | Fast | Good | Low | Development, testing |
| `gpt-4o` | Medium | Excellent | Medium | Production use |
| `gpt-4-turbo` | Slow | Best | High | Complex scenarios |

---

## 🎨 UI Components

### Floating Button
- **Location:** Bottom-right corner
- **Color:** Purple-to-blue gradient
- **Icon:** Lightbulb
- **Click:** Opens AI panel

### AI Chat Panel
- **Size:** 400px × 600px
- **Position:** Above the button
- **Features:**
  - Chat history
  - Message bubbles (user vs AI)
  - Text input area
  - Send button
  - Apply button (on success)

### Auto-Hide Feature
- AI button automatically hides if `AI_ENABLED=false`
- Graceful degradation if API key invalid
- Error messages shown in chat

---

## 🔧 API Endpoints

### Generate Mapping
```bash
POST /api/ai/generate
Content-Type: application/json

{
  "userPrompt": "Create GET /api/users endpoint",
  "taskType": "CREATE_MAPPING"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Mapping generated successfully",
  "generatedMapping": {
    "name": "Get Users",
    "endpointType": "REST",
    "request": {
      "method": "GET",
      "path": "/api/users"
    },
    "response": {
      "status": 200,
      "body": "{\"users\": [...]}"
    }
  }
}
```

### Check AI Status
```bash
GET /api/ai/status
```

**Response:**
```json
{
  "enabled": true,
  "provider": "OpenAI"
}
```

---

## 🚨 Troubleshooting

### Issue: AI Button Not Appearing

**Solution:**
1. Check `AI_ENABLED=true` in `.env`
2. Restart the application
3. Clear browser cache
4. Check browser console for errors

---

### Issue: "AI Assistant is not enabled" Error

**Solution:**
```bash
# Set environment variables
export AI_ENABLED=true
export OPENAI_API_KEY=sk-proj-your-key-here

# Or update .env file
echo "AI_ENABLED=true" >> .env
echo "OPENAI_API_KEY=sk-proj-..." >> .env

# Restart application
mvn spring-boot:run
```

---

### Issue: "Failed to generate mapping" Error

**Possible Causes:**
1. **Invalid API Key:** Check `OPENAI_API_KEY` is correct
2. **API Quota Exceeded:** Check OpenAI dashboard for usage limits
3. **Network Issue:** Verify internet connection
4. **Model Not Available:** Try different model (e.g., `gpt-4o-mini`)

**Solution:**
```bash
# Test API key manually
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"

# If error, generate new key at: https://platform.openai.com/api-keys
```

---

### Issue: AI Generates Incorrect Mapping

**Solutions:**
1. **Be More Specific:** Add more details to your prompt
2. **Iterate:** Ask AI to fix specific issues
3. **Manual Edit:** Click "Apply Mapping" then edit the form
4. **Add Examples:** Create one mapping manually, AI learns from it

---

## 💰 Cost Considerations

### Token Usage

**Per Request:**
- Context: ~500 tokens (10 existing mappings)
- User prompt: ~50 tokens
- AI response: ~500 tokens
- **Total:** ~1050 tokens per generation

### Pricing (as of 2024)

| Model | Input Cost | Output Cost | Per Request |
|-------|-----------|-------------|-------------|
| gpt-4o-mini | $0.15/1M tokens | $0.60/1M tokens | $0.0004 |
| gpt-4o | $2.50/1M tokens | $10.00/1M tokens | $0.0068 |

**Example:** 1000 mappings generated with `gpt-4o-mini` = **$0.40**

---

## 🔒 Security & Privacy

### Data Sent to OpenAI

**Included:**
- Your prompt
- Existing endpoint paths and methods
- Response status codes
- Tag names
- Priorities

**NOT Included:**
- Actual response bodies (truncated)
- Sensitive data from requests/responses
- User credentials
- Database connection strings

### Privacy Mode (Future)

Option to strip sensitive data before sending to AI:
```yaml
simulator:
  ai:
    privacy-mode: anonymized  # full, anonymized, local-only
```

---

## 🎓 Advanced Usage

### Batch Creation

```
Create CRUD endpoints for products:
- GET /api/products (list all)
- GET /api/products/{id} (get one)
- POST /api/products (create)
- PUT /api/products/{id} (update)
- DELETE /api/products/{id} (delete)
```

AI can generate all 5 at once (future feature - currently one at a time).

### Templating Requests

```
Create user endpoint with dynamic ID and timestamp in response
```

AI will use: `{{request.pathSegments.[2]}}` and `{{now}}`

### Error Scenarios

```
Create flaky endpoint with 25% error rate for testing resilience
```

AI generates chaos configuration automatically.

---

## 📈 Metrics & Analytics

Check AI usage in logs:
```bash
grep "AI generate request" backend/logs/application.log
```

Monitor OpenAI usage:
https://platform.openai.com/usage

---

## 🛣️ Roadmap

### Phase 2 (Coming Soon)
- [ ] **Batch Creation:** Generate multiple related endpoints
- [ ] **OpenAPI Import:** Paste Swagger spec, generate all endpoints
- [ ] **Debugging Assistant:** "Why isn't /api/users matching?"
- [ ] **Optimization Suggestions:** "Your priorities could be improved..."

### Phase 3
- [ ] **GraphQL Schema Import:** Auto-generate from SDL
- [ ] **Test Generation:** Create cURL commands for testing
- [ ] **Documentation Export:** Generate API docs from mappings
- [ ] **Migration Assistant:** "Clone all /v1 endpoints to /v2"

---

## 🤝 Contributing

To enhance the AI Assistant:

1. **Improve Prompts:** Edit `AIService.java` system prompt
2. **Add Features:** Extend `AIRequest.AITaskType` enum
3. **UI Enhancements:** Modify `ai-assistant.html` template
4. **Context Logic:** Tune `AIContextService.java` relevance scoring

---

## 📚 Resources

- **OpenAI API Docs:** https://platform.openai.com/docs
- **API Simulator Docs:** See CLAUDE.md
- **GitHub Issues:** Report bugs or request features

---

**Made with 🤖 by the API Simulator Team**
