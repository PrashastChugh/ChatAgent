# ChatAgent — Agentic AI Chat Application

A production-grade **Agentic AI Chat Application** built with Java 17 and Spring Boot.

This is not a simple chatbot. It implements a full **ReAct (Reason + Act) agent loop** — the same pattern used by production AI systems like AutoGPT, LangChain agents, and OpenAI Assistants. The agent reasons step-by-step, calls real tools (search, database, calculator), observes the results, and loops until it produces a final answer.

---

## What Makes This "Agentic"?

A normal chatbot does this:

```
User: "What is the weather in Paris?"
Bot:  "I don't have real-time data." ← useless
```

An **agent** does this:

```
User: "What is the weather in Paris?"

Agent THOUGHT:    "I need real-time weather. I should call get_weather."
Agent ACTION:     calls get_weather(city="Paris")
Agent OBSERVATION:"18°C, sunny, wind 12km/h"
Agent THOUGHT:    "I have the data. I can answer now."
Agent ANSWER:     "It's 18°C and sunny in Paris right now!"
```

Every one of those steps (THOUGHT, ACTION, OBSERVATION, ANSWER) is persisted to the database — giving you full auditability of the AI's reasoning.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 17 | LTS, widely used in enterprise |
| Framework | Spring Boot 3.2 | Industry standard for microservices |
| Real-time | WebSockets (STOMP + SockJS) | Persistent connection — server pushes messages to client |
| Async | Apache Kafka | Decouples message receipt from AI processing |
| Database | PostgreSQL + Flyway | Reliable persistence + version-controlled schema |
| Cache | Redis | Sub-millisecond agent memory lookups |
| AI | OpenAI GPT-4o | State-of-the-art LLM for reasoning |
| HTTP Client | Spring WebFlux (WebClient) | Non-blocking calls to external tool APIs |
| Build | Maven | Dependency management + reproducible builds |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │  WebSocket (STOMP over SockJS)
                            │  connects to: ws://localhost:8080/ws
                            │  sends to:    /app/chat.send
                            │  listens on:  /topic/chat/{conversationId}
┌───────────────────────────▼─────────────────────────────────────┐
│                     SPRING BOOT APPLICATION                     │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              ChatWebSocketController                     │   │
│  │  @MessageMapping("/chat.send")                           │   │
│  │  Validates input, ACKs immediately, hands off to service │   │
│  └──────────────────┬───────────────────────────────────────┘   │
│                     │                                           │
│  ┌──────────────────▼───────────────────────────────────────┐   │
│  │                    ChatService                           │   │
│  │  1. Persist user message to PostgreSQL                   │   │
│  │  2. Publish ChatEvent to Kafka (returns immediately)     │   │
│  │  3. Send "processing..." to client via WebSocket         │   │
│  └──────────────────┬───────────────────────────────────────┘   │
│                     │  Kafka: chat.message.inbound topic         │
│  ┌──────────────────▼───────────────────────────────────────┐   │
│  │                  AgentConsumer                           │   │
│  │  Kafka listener — picks up the event asynchronously     │   │
│  │  Triggers AgentOrchestrator in agentExecutor thread pool │   │
│  └──────────────────┬───────────────────────────────────────┘   │
│                     │                                           │
│  ┌──────────────────▼───────────────────────────────────────┐   │
│  │              AgentOrchestrator (ReAct Loop)              │   │
│  │                                                          │   │
│  │  while (not done && iterations < maxIterations):         │   │
│  │    1. Build prompt from memory + conversation history    │   │
│  │    2. Call LLM → get THOUGHT + ACTION                    │   │
│  │    3. If ACTION: route to tool via ToolRegistry          │   │
│  │    4. Execute tool → get OBSERVATION                     │   │
│  │    5. Persist AgentStep (THOUGHT/ACTION/OBSERVATION)     │   │
│  │    6. Feed observation back into next LLM call           │   │
│  │  end                                                     │   │
│  │  7. Persist FINAL_ANSWER as assistant Message            │   │
│  │  8. Broadcast response via WebSocket                     │   │
│  │                                                          │   │
│  │  ┌─────────────┐  ┌────────────────────────────────┐    │   │
│  │  │  LlmClient  │  │         ToolRegistry            │    │   │
│  │  │  (OpenAI)   │  │  auto-discovers @AgentTool beans│    │   │
│  │  └─────────────┘  │  ┌────────────────────────────┐│    │   │
│  │                   │  │ WebSearchTool               ││    │   │
│  │  ┌─────────────┐  │  │ DatabaseQueryTool           ││    │   │
│  │  │MemoryManager│  │  │ CalculatorTool              ││    │   │
│  │  │Redis+Postgres│ │  └────────────────────────────┘│    │   │
│  │  └─────────────┘  └────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            PostgreSQL  (Persistence)                     │   │
│  │   conversations | messages | agent_steps                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            Redis  (Short-term Memory Cache)              │   │
│  │   conversation-memory:{id} → last 20 messages (1h TTL)   │   │
│  │   tool-results:{hash}      → cached tool outputs (5m TTL)│   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Database Schema

### `conversations`
Represents one chat session. Soft-deleted (never hard-deleted).

| Column | Type | Description |
|---|---|---|
| id | UUID | Primary key (unguessable) |
| user_id | VARCHAR | Owner of the conversation |
| title | VARCHAR | Auto-generated or user-defined |
| is_deleted | BOOLEAN | Soft delete flag |
| created_at | TIMESTAMP | When the session started |
| updated_at | TIMESTAMP | Last activity |

### `messages`
Every message exchanged — user, assistant, system, tool results.

| Column | Type | Description |
|---|---|---|
| id | UUID | Primary key |
| conversation_id | UUID | FK → conversations |
| role | ENUM | USER / ASSISTANT / SYSTEM / TOOL |
| content | TEXT | The actual message text |
| token_count | INTEGER | For cost tracking |
| model_used | VARCHAR | Which LLM generated this |
| created_at | TIMESTAMP | When it was sent |

### `agent_steps`
Full audit trail of every reasoning step. The "black box recorder" for the AI.

| Column | Type | Description |
|---|---|---|
| id | UUID | Primary key |
| message_id | UUID | FK → messages |
| step_number | INTEGER | Ordering within one agent run |
| step_type | ENUM | THOUGHT / ACTION / OBSERVATION / FINAL_ANSWER |
| thought | TEXT | Agent's internal reasoning text |
| tool_name | VARCHAR | Which tool was called (ACTION steps) |
| tool_input | TEXT | JSON args sent to the tool |
| tool_output | TEXT | Result returned by the tool |
| duration_ms | BIGINT | How long this step took |
| is_error | BOOLEAN | Did the tool fail? |
| error_message | TEXT | The error if is_error=true |
| created_at | TIMESTAMP | When this step ran |

---

## Project Structure

```
src/main/java/com/agentichat/
│
├── AgenticChatApplication.java
│
├── config/
│   ├── WebSocketConfig.java             # STOMP endpoints, broker config, thread pools
│   ├── KafkaConfig.java                 # Topics, serializers, consumer group
│   ├── RedisConfig.java                 # JSON serialization, cache TTLs
│   ├── AsyncConfig.java                 # Bounded thread pools (agentExecutor, taskExecutor)
│   └── LlmConfig.java                   # OpenAI client setup, timeout, retry
│
├── websocket/
│   └── ChatWebSocketController.java     # @MessageMapping — handles incoming WS messages
│
├── controller/
│   └── ConversationController.java      # REST: GET history, POST new session, GET agent steps
│
├── service/
│   ├── ChatService.java                 # Orchestrates: persist → publish Kafka → ACK client
│   └── ConversationService.java         # Business logic for conversation management
│
├── kafka/
│   ├── MessageProducer.java             # Publishes ChatEvent to Kafka
│   └── AgentConsumer.java               # Listens on Kafka, triggers AgentOrchestrator
│
├── agent/
│   ├── AgentOrchestrator.java           # ReAct loop: the core of the entire application
│   ├── AgentContext.java                # Holds all state for one agent run
│   ├── LlmClient.java                   # Interface — swap LLM providers freely
│   ├── OpenAiLlmClient.java             # OpenAI implementation
│   ├── memory/
│   │   ├── MemoryManager.java           # Builds context window: Redis first, PostgreSQL fallback
│   │   └── RedisMemoryCache.java        # Redis operations for conversation memory
│   └── tools/
│       ├── AgentTool.java               # Interface every tool must implement
│       ├── ToolRegistry.java            # Auto-discovers all @AgentTool beans at startup
│       ├── WebSearchTool.java
│       ├── DatabaseQueryTool.java
│       └── CalculatorTool.java
│
├── model/
│   ├── entity/
│   │   ├── Conversation.java
│   │   ├── Message.java
│   │   └── AgentStep.java
│   └── dto/
│       ├── ChatMessageDto.java
│       ├── ConversationDto.java
│       └── AgentStepDto.java
│
├── repository/
│   ├── ConversationRepository.java
│   ├── MessageRepository.java
│   └── AgentStepRepository.java
│
└── exception/
    ├── AgentException.java
    ├── ToolExecutionException.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yml
├── application-dev.yml
└── db/migration/
    ├── V1__create_conversations.sql
    ├── V2__create_messages.sql
    └── V3__create_agent_steps.sql
```

---

## Key Engineering Decisions

### Why Kafka between the WebSocket and the Agent?
The agent loop takes 5–30 seconds (multiple LLM calls + tool calls). If we ran it synchronously on the WebSocket thread, the user would stare at a frozen screen. Kafka decouples receipt from processing — the server ACKs the message instantly, the agent runs in the background, then pushes the result back via WebSocket when done.

### Why persist AgentSteps?
Most AI apps are black boxes. Persisting every reasoning step gives us:
- **Debugging** — when the agent gives a wrong answer, see exactly where its reasoning broke
- **Auditing** — compliance, logging, explainability
- **Analytics** — which tools are called most? How many steps does the average query take?

### Why the ToolRegistry pattern?
Tools are Spring beans annotated with `@AgentTool`. The registry auto-discovers them at startup via Spring's component scan. Adding a new tool = adding one class. Zero changes to any existing code. This is the **Open/Closed Principle** in practice.

### Why soft delete?
Conversations are user data. Hard deleting is irreversible and can violate data retention laws (GDPR). Soft delete (`is_deleted = true`) lets you recover data and comply with regulations.

### Why UUID primary keys?
Auto-increment integers (1, 2, 3...) are sequential and guessable. UUIDs are random and unguessable, providing a basic layer of access security.

### Why two thread pools?
The `agentExecutor` handles slow agent loops. The `taskExecutor` handles fast tasks like Kafka publishing. Separate pools = **bulkhead pattern** — one domain can't take down the other.

---

## WebSocket Message Flow

```
1. Client connects:    ws://localhost:8080/ws
2. Client subscribes:  SUBSCRIBE /topic/chat/{conversationId}
3. Client sends:       SEND /app/chat.send  { "conversationId": "...", "content": "..." }
4. Server ACKs:        SEND /topic/chat/{id}  { "processing": true, "content": "Thinking..." }
5. Agent runs async:   THOUGHT → ACTION → OBSERVATION → FINAL_ANSWER
6. Server pushes:      SEND /topic/chat/{id}  { "role": "ASSISTANT", "content": "..." }
```

---

## API Endpoints

### WebSocket (STOMP)
| Destination | Direction | Description |
|---|---|---|
| `/app/chat.send` | Client → Server | Send a chat message |
| `/topic/chat/{id}` | Server → Client | Receive AI responses |
| `/user/queue/errors` | Server → Client | Receive error notifications |

### REST API
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/conversations` | Create a new conversation |
| `GET` | `/api/v1/conversations?userId=X` | List user's conversations (paginated) |
| `GET` | `/api/v1/conversations/{id}/messages` | Full message history |
| `GET` | `/api/v1/conversations/{id}/messages/{msgId}/steps` | Agent reasoning chain |
| `DELETE` | `/api/v1/conversations/{id}` | Soft-delete a conversation |

---

## Running Locally

### Prerequisites
- Java 17+
- Docker + Docker Compose
- OpenAI API key

### Start infrastructure
```bash
docker compose up -d
```

### Set your API key
```bash
export OPENAI_API_KEY=sk-your-key-here
```

### Run the app
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

App starts on `http://localhost:8080`

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes | Your OpenAI API key |
| `DB_URL` | Prod only | PostgreSQL connection URL |
| `DB_PASSWORD` | Prod only | PostgreSQL password |
| `REDIS_HOST` | Prod only | Redis host |
| `KAFKA_SERVERS` | Prod only | Kafka bootstrap servers |

---

## Roadmap

### Phase 1 — Core (current)
- [x] Project setup, config, WebSocket
- [x] Database schema (conversations, messages, agent_steps)
- [ ] WebSocket controller + ChatService
- [ ] Kafka producer + consumer
- [ ] ReAct agent loop
- [ ] Tool system (WebSearch, DB, Calculator)
- [ ] Memory management (Redis + PostgreSQL)

### Phase 2 — Production Hardening
- [ ] Docker Compose for local full-stack
- [ ] JWT authentication (Spring Security)
- [ ] Rate limiting per user (Bucket4j)
- [ ] Global error handling + structured error responses
- [ ] Actuator health endpoints + Prometheus metrics
- [ ] API documentation (SpringDoc OpenAPI / Swagger UI)

### Phase 3 — Advanced Features
- [ ] Streaming responses (agent streams tokens in real time via WebSocket)
- [ ] Multi-agent support (orchestrator delegates to specialist sub-agents)
- [ ] File upload tool (agent can read PDFs, CSVs)
- [ ] Conversation summarization (auto-compress old context to save tokens)
- [ ] Tool result caching (don't call the same search query twice)
- [ ] Admin dashboard — tool usage analytics, agent step viewer
- [ ] Support for multiple LLM providers (Anthropic Claude, local Ollama)

### Phase 4 — Scale
- [ ] Kubernetes deployment manifests
- [ ] Horizontal scaling (stateless app + Redis session sharing)
- [ ] Kafka partition strategy for user-based load distribution
- [ ] Read replicas for PostgreSQL

---

## Architecture Patterns Used

| Pattern | Where | Why |
|---|---|---|
| ReAct (Reason+Act) | AgentOrchestrator | Core agentic AI loop |
| Strategy | LlmClient interface | Swap LLM providers without changing agent code |
| Registry | ToolRegistry | Auto-discover tools via Spring |
| Bulkhead | AsyncConfig | Isolate slow agent threads from fast task threads |
| Saga | Kafka flow | Message receipt → async agent → WebSocket push |
| Soft Delete | Conversations | Safe, recoverable data deletion |
| DTO | model/dto | Never expose JPA entities to API consumers |

---

## Interview Talking Points

1. **"Why not just use LangChain4j?"** — I built the agent loop from scratch to understand the internals. LangChain4j is a valid choice for production but abstracts away the details. I can use either.

2. **"How does the agent avoid infinite loops?"** — `max-iterations: 10` in config + a hard stop in the orchestrator. After 10 iterations, it forces a FINAL_ANSWER.

3. **"What happens if Kafka is down?"** — The producer has `retries: 3`. For true resilience, we'd add an outbox pattern (write Kafka events to DB first, a scheduler publishes them).

4. **"How do you handle tool failures?"** — Tools return a typed result with an `isError` flag. The agent observes the error and decides whether to retry, use a different tool, or tell the user it couldn't complete the task.

5. **"How is this different from just calling ChatGPT?"** — ChatGPT is a single prompt-response. This is a **loop** — the model can take actions, observe real-world results, and update its reasoning. It's the difference between asking someone a question and hiring someone to research and answer it.

---

## Author

**Prashast Chugh**
GitHub: [@PrashastChugh](https://github.com/PrashastChugh)
