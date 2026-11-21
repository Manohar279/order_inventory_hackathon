# Order Service: Inventory Reservation & Audit Trail

A synchronous Spring Boot service that accepts customer orders, reserves stock all-or-nothing,
stores every outcome (CONFIRMED / REJECTED), and records an audit trail. Everything for one
order (order row, items, stock deduction, audit entry) commits in **one database transaction**.

## Prerequisites
- Java 17 or later (`java -version`)
- Maven 3.8+ (`mvn -version`)
- No external infrastructure: H2 runs in memory.

## Run
```bash
mvn spring-boot:run
```
The app starts on **http://localhost:8080**. Seed data loads on every start, so **restarting
the app resets all stock and orders** (use this between demo scenarios).

Run the tests:
```bash
mvn test
```

## H2 console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:ordersdb`
- User: `sa`, password: *(empty)*
- Tables: `PRODUCTS`, `INVENTORY`, `ORDERS`, `ORDER_ITEMS`, `AUDIT_LOG`

## Seed data (`src/main/resources/data.sql`)
| ID | Product | Price (INR) | Stock |
|----|---------|-------------|-------|
| 101 | Laptop | 50000 | 10 |
| 102 | Mouse | 800 | 25 |
| 103 | Keyboard | 1500 | 15 |
| 104 | Monitor | 12000 | 5 |
| 105 | Headphones | 2000 | 0 |

## API
| Method | Endpoint | Purpose | Status codes |
|--------|----------|---------|--------------|
| POST | `/api/orders` | Submit and process an order | 201 CONFIRMED · 200 REJECTED · 400 invalid/unknown product |
| GET | `/api/orders/{id}` | Order details and final status | 200 · 404 |
| GET | `/api/products` | Products with available stock | 200 |
| GET | `/api/audit/{orderId}` | Audit history for an order | 200 · 404 |
| POST | `/api/orders/{id}/cancel` | *(bonus)* Cancel and restore stock | 200 · 404 · 409 |

Request:
```json
{"customerId":"CUST001","items":[{"productId":101,"quantity":1},{"productId":102,"quantity":2}]}
```
Optional `"requestId":"REQ-1"` makes the submission idempotent (bonus).

Success (201):
```json
{"orderId":1001,"customerId":"CUST001","status":"CONFIRMED","totalAmount":51600.00,
 "message":"Order confirmed successfully","createdAt":"...","items":[...]}
```
Insufficient stock (200, order is stored and retrievable):
```json
{"orderId":1002,"status":"REJECTED","totalAmount":2000.00,
 "message":"Insufficient stock for product 105 (requested 1, available 0)", ...}
```
Error (every 4xx/5xx has this shape):
```json
{"timestamp":"...","status":400,"error":"Bad Request","message":"Unknown product id(s): [999]",
 "path":"/api/orders","details":["productId 999 does not exist"]}
```

## Demo scenarios
Run `./demo.sh` (needs `curl`; `jq` optional) or import `postman_collection.json`.
Restart the app first so stock is at seed values.

| Test | Input | Expected |
|------|-------|----------|
| TC01 | 101×1 + 102×2 | 201 CONFIRMED, total 51600; stock 101: 10→9, 102: 25→23 |
| TC02 | 105×1 | 200 REJECTED; no stock deducted |
| TC03 | unknown product 999 | 400; inventory unchanged |
| TC04 | GET order from TC01 | correct items, amount 51600, CONFIRMED |
| TC05 | 101×1 + 103×2 + 105×1 | 200 REJECTED; 101 and 103 **not** partially deducted |

## Design choices
- **One transaction per order** (`OrderService.placeOrder` is `@Transactional`): the order,
  its items, the stock deduction and the audit entry commit or roll back together.
  `AuditService.record` uses `Propagation.MANDATORY`, so it can only run inside that transaction.
- **All-or-nothing check:** every item is checked *before* any stock is touched. If one item
  is short, nothing is deducted.
- **Rejection is a business outcome, not an exception:** the REJECTED order is committed so it
  stays retrievable by ID, with its own audit entry. (Throwing and rolling back would erase it.)
- **Invalid input never reaches the DB:** Bean Validation and the unknown-product check run first,
  return 400, and write nothing.
- **Concurrency:** inventory rows are read with `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`),
  locked in product-id order to avoid deadlocks. Concurrent orders for the same product queue up,
  so stock can't be oversold. A DB `CHECK (available_qty >= 0)` is a final safety net.
- **Price snapshot:** each order item stores `unit_price` and `product_name` at order time.
- **Duplicate lines** for the same product in one request are merged before checking stock.
- **Order IDs start at 1001**, matching the brief's examples.

## Bonus features
- Concurrent orders without overselling (pessimistic lock + test with 10 parallel orders)
- Cancellation with inventory restoration: `POST /api/orders/{id}/cancel`
- Idempotent submission via `requestId`
- Automated integration tests: `OrderFlowIntegrationTest`

## Project structure
```
src/main/java/com/hackathon/orders
├── controller/   OrderController, ProductController, AuditController
├── service/      OrderService (core logic), AuditService, ProductService
├── repository/   Spring Data JPA repositories (InventoryRepository has the lock query)
├── entity/       Product, Inventory, CustomerOrder, OrderItem, AuditLog, OrderStatus
├── dto/          Request/response records, ErrorResponse
└── exception/    Custom exceptions + GlobalExceptionHandler
```
