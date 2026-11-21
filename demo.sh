#!/usr/bin/env bash
# Runs the mandatory demo scenarios against a freshly started app.
# Usage: restart the app (mvn spring-boot:run), then ./demo.sh
BASE=${BASE:-http://localhost:8080}
PRETTY=cat
command -v jq >/dev/null 2>&1 && PRETTY="jq ."

call() {  # call METHOD PATH [BODY]
  echo; echo ">>> $1 $2 ${3:-}"
  if [ -n "${3:-}" ]; then
    curl -s -w "\nHTTP %{http_code}\n" -X "$1" "$BASE$2" -H "Content-Type: application/json" -d "$3"
  else
    curl -s -w "\nHTTP %{http_code}\n" -X "$1" "$BASE$2"
  fi
}

echo "===== Stock BEFORE ====="
call GET /api/products

echo; echo "===== TC01: 101x1 + 102x2 -> CONFIRMED, total 51600 ====="
call POST /api/orders '{"customerId":"CUST001","items":[{"productId":101,"quantity":1},{"productId":102,"quantity":2}]}'

echo; echo "===== Stock AFTER TC01 (101: 9, 102: 23) ====="
call GET /api/products

echo; echo "===== TC02: 105x1 -> REJECTED, no deduction ====="
call POST /api/orders '{"customerId":"CUST001","items":[{"productId":105,"quantity":1}]}'

echo; echo "===== TC03: unknown product -> 400 ====="
call POST /api/orders '{"customerId":"CUST001","items":[{"productId":999,"quantity":1}]}'

echo; echo "===== TC04: GET order 1001 ====="
call GET /api/orders/1001

echo; echo "===== TC05: 101x1 + 103x2 + 105x1 -> REJECTED, no partial deduction ====="
call POST /api/orders '{"customerId":"CUST002","items":[{"productId":101,"quantity":1},{"productId":103,"quantity":2},{"productId":105,"quantity":1}]}'

echo; echo "===== Validation: quantity 0 -> 400 ====="
call POST /api/orders '{"customerId":"CUST001","items":[{"productId":101,"quantity":0}]}'

echo; echo "===== Audit trails ====="
call GET /api/audit/1001
call GET /api/audit/1002

echo; echo "===== Stock FINAL (101 still 9, 103 still 15) ====="
call GET /api/products
